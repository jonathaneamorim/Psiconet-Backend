package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.ConflictException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.mail.EmailService;
import com.psiconet.model.dtos.clinical.record.AccessRequestDTO;
import com.psiconet.model.dtos.clinical.record.ExportedPdfDTO;
import com.psiconet.model.dtos.clinical.record.PatientRecordLinkDTO;
import com.psiconet.model.dtos.clinical.record.PatientRecordViewDTO;
import com.psiconet.model.dtos.clinical.record.SessionNoteDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.MedicalRecord;
import com.psiconet.model.entities.clinical.RecordAccessRequest;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.ClinicalAuditActionEnum;
import com.psiconet.model.enums.clinical.RecordAccessStatusEnum;
import com.psiconet.model.enums.clinical.SessionNoteStatusEnum;
import com.psiconet.model.enums.clinical.SessionNoteTypeEnum;
import com.psiconet.repositories.clinical.MedicalRecordRepository;
import com.psiconet.repositories.clinical.RecordAccessRequestRepository;
import com.psiconet.repositories.clinical.SessionNoteRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.services.interfaces.clinical.PatientMedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.psiconet.services.implement.clinical.ClinicalRecordSupport.recordField;

/**
 * O paciente NÃO tem acesso automático: solicita (LGPD art. 18) e, se o psicólogo liberar, lê em
 * modo somente leitura apenas o registro documental (campos do prontuário + registros de evolução
 * finalizados e seus adendos). Anotações pessoais de trabalho (PRIVATE) e rascunhos nunca aparecem.
 * Vale também para vínculos encerrados.
 */
@Service
@RequiredArgsConstructor
public class PatientMedicalRecordServiceImpl implements PatientMedicalRecordService {

    private final TreatmentLinkRepository linkRepository;
    private final MedicalRecordRepository recordRepository;
    private final RecordAccessRequestRepository accessRepository;
    private final SessionNoteRepository noteRepository;
    private final ClinicalRecordSupport support;
    private final ClinicalAuditService audit;
    private final MedicalRecordPdfService pdfService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<PatientRecordLinkDTO> listMyLinks(User patient) {
        ensurePatient(patient);
        Map<UUID, RecordAccessRequest> latestByLink = accessRepository.findByPatientOrderByRequestedAtDesc(patient).stream()
                .collect(Collectors.toMap(r -> r.getMedicalRecord().getTreatmentLink().getId(), Function.identity(), (first, other) -> first));

        return linkRepository.findByPatient_User_Id(patient.getId()).stream()
                .map(link -> {
                    RecordAccessRequest latest = latestByLink.get(link.getId());
                    return new PatientRecordLinkDTO(
                            link.getId(),
                            link.getPsychologist().getUser().getFullName(),
                            link.getPsychologist().getCrp(),
                            Boolean.TRUE.equals(link.getIsActive()),
                            latest == null ? null : support.toAccessDTO(latest, true));
                })
                .sorted(Comparator.comparing(PatientRecordLinkDTO::linkActive).reversed())
                .toList();
    }

    @Override
    @Transactional
    public AccessRequestDTO requestAccess(User patient, UUID treatmentLinkId) {
        ensurePatient(patient);
        support.ensureAvailable();
        TreatmentLink link = linkRepository.findWithPartiesById(treatmentLinkId)
                .filter(l -> l.getPatient().getUser().getId().equals(patient.getId()))
                .orElseThrow(() -> new EntityNotFoundException(TreatmentLink.class, treatmentLinkId));

        // Um prontuário por vínculo: se o psicólogo ainda não abriu, abre vazio para registrar o pedido
        MedicalRecord record = recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link)
                .orElseGet(() -> support.createRecord(link));

        if (accessRepository.existsByMedicalRecordAndPatientAndStatus(record, patient, RecordAccessStatusEnum.PENDING)) {
            throw new ConflictException(null, "Você já tem uma solicitação aguardando resposta para este prontuário.");
        }

        User psychologist = link.getPsychologist().getUser();
        RecordAccessRequest request = new RecordAccessRequest();
        request.setMedicalRecord(record);
        request.setPatient(patient);
        request.setPsychologist(psychologist);
        request.setStatus(RecordAccessStatusEnum.PENDING);
        accessRepository.saveAndFlush(request);

        audit.record(patient, ClinicalAuditActionEnum.ACCESS_REQUESTED, record.getId(), request.getId());
        // Aviso sem identificar o paciente (o nome aparece só dentro da tela de solicitações)
        notificationService.create(psychologist, patient, NotificationType.MEDICAL_RECORD_ACCESS_REQUESTED,
                "Nova solicitação de acesso a prontuário",
                "Um(a) paciente solicitou acesso ao próprio prontuário. Analise e registre sua decisão.",
                "MEDICAL_RECORD", request.getId());
        emailService.sendRecordAccessRequested(psychologist.getEmail(), psychologist.getFullName());

        return support.toAccessDTO(request, false);
    }

    @Override
    @Transactional
    public PatientRecordViewDTO viewGranted(User patient, UUID accessRequestId) {
        RecordAccessRequest request = activeGrant(patient, accessRequestId);
        audit.record(patient, ClinicalAuditActionEnum.PATIENT_RECORD_VIEWED, request.getMedicalRecord().getId(), request.getId());
        return buildView(request);
    }

    @Override
    @Transactional
    public ExportedPdfDTO exportPdf(User patient, UUID accessRequestId) {
        RecordAccessRequest request = activeGrant(patient, accessRequestId);
        if (!request.isExportAllowed()) {
            throw new BusinessException("export", "A exportação em PDF não foi autorizada nesta liberação.");
        }
        UUID exportId = UUID.randomUUID();
        byte[] pdf = pdfService.render(buildView(request), exportId);
        String sha256 = sha256(pdf);
        // O hash do arquivo fica na trilha de auditoria (alvo = id da exportação + hash)
        audit.record(patient, ClinicalAuditActionEnum.PDF_EXPORTED, request.getMedicalRecord().getId(),
                exportId + ":" + sha256.substring(0, 16));
        return new ExportedPdfDTO(pdf, sha256, "prontuario-psiconet-" + exportId.toString().substring(0, 8) + ".pdf");
    }

    // ------------------------------------------------------------------

    private RecordAccessRequest activeGrant(User patient, UUID accessRequestId) {
        ensurePatient(patient);
        support.ensureAvailable();
        RecordAccessRequest request = accessRepository.findByIdAndPatient(accessRequestId, patient)
                .orElseThrow(() -> new EntityNotFoundException(RecordAccessRequest.class, accessRequestId));
        if (!request.isActiveGrant(LocalDateTime.now())) {
            audit.record(patient, ClinicalAuditActionEnum.ACCESS_DENIED_ATTEMPT, request.getMedicalRecord().getId(), request.getId());
            throw new AccessDeniedException(request.getStatus() == RecordAccessStatusEnum.GRANTED
                    ? "O prazo de acesso liberado terminou. Faça uma nova solicitação."
                    : "Acesso não liberado.");
        }
        return request;
    }

    private PatientRecordViewDTO buildView(RecordAccessRequest request) {
        MedicalRecord r = request.getMedicalRecord();
        TreatmentLink link = r.getTreatmentLink();
        List<SessionNoteDTO> notes = noteRepository
                .findByMedicalRecordAndTypeAndStatusOrderByFinalizedAtAsc(r, SessionNoteTypeEnum.RECORD, SessionNoteStatusEnum.FINALIZED)
                .stream()
                .map(n -> support.toNoteDTO(n, false, true))
                .toList();

        return new PatientRecordViewDTO(
                request.getId(),
                link.getPatient().getUser().getFullName(),
                link.getPsychologist().getUser().getFullName(),
                link.getPsychologist().getCrp(),
                r.getOpeningDate(),
                support.decrypt(r, recordField(r, "demand"), r.getDemandEncrypted()),
                support.decrypt(r, recordField(r, "goals"), r.getGoalsEncrypted()),
                support.decrypt(r, recordField(r, "history"), r.getHistoryEncrypted()),
                support.decrypt(r, recordField(r, "procedures"), r.getProceduresEncrypted()),
                support.decrypt(r, recordField(r, "closing"), r.getClosingEncrypted()),
                notes,
                request.getExpiresAt(),
                request.isExportAllowed());
    }

    private static void ensurePatient(User user) {
        if (user.getRole() != RoleEnum.PATIENT) {
            throw new AccessDeniedException("Somente o(a) paciente titular solicita acesso ao próprio prontuário.");
        }
    }

    private static String sha256(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
