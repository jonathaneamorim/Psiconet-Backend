package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.mail.EmailService;
import com.psiconet.infra.richtext.RichTextSanitizer;
import com.psiconet.infra.text.TextSanitizer;
import com.psiconet.model.dtos.clinical.record.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.*;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.ClinicalAuditActionEnum;
import com.psiconet.model.enums.clinical.RecordAccessStatusEnum;
import com.psiconet.model.enums.clinical.SessionNoteStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.clinical.*;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.services.interfaces.clinical.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.psiconet.services.implement.clinical.ClinicalRecordSupport.*;

/**
 * Regras de acesso (ADR 008/009):
 * <ul>
 *   <li>Somente o psicólogo TITULAR do vínculo lê/edita. Qualquer outro usuário recebe 404 (não
 *       confirma a existência) e a tentativa é auditada.</li>
 *   <li>Vínculo ativo: pode editar o prontuário e criar anotações. Vínculo encerrado: o psicólogo
 *       mantém a guarda (leitura) e pode registrar adendos/retificações, mas não novas anotações.</li>
 *   <li>Todo acesso e alteração gera registro na auditoria clínica encadeada.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private static final int MAX_GRANT_DAYS = 365;

    private final TreatmentLinkRepository linkRepository;
    private final MedicalRecordRepository recordRepository;
    private final SessionNoteRepository noteRepository;
    private final SessionNoteAddendumRepository addendumRepository;
    private final RecordAccessRequestRepository accessRepository;
    private final ClinicalAuditLogRepository auditRepository;
    private final AppointmentRepository appointmentRepository;
    private final PsychologistRepository psychologistRepository;
    private final UserRepository userRepository;
    private final ClinicalRecordSupport support;
    private final ClinicalAuditService audit;
    private final RichTextSanitizer richText;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final MedicalRecordPdfService pdfService;

    // ------------------------------------------------------------------ prontuário

    @Override
    @Transactional(readOnly = true)
    public List<MedicalRecordSummaryDTO> listMine(User psychologist) {
        ensurePsychologist(psychologist);
        List<TreatmentLink> links = linkRepository.findByPsychologist_User_Id(psychologist.getId());
        Map<UUID, MedicalRecord> records = recordRepository.findByTreatmentLinkIn(links).stream()
                .collect(Collectors.toMap(r -> r.getTreatmentLink().getId(), Function.identity(), (a, b) -> a));

        return links.stream()
                .map(link -> {
                    MedicalRecord r = records.get(link.getId());
                    return new MedicalRecordSummaryDTO(
                            link.getId(),
                            r == null ? null : r.getId(),
                            link.getPatient().getUser().getId(),
                            link.getPatient().getUser().getFullName(),
                            Boolean.TRUE.equals(link.getIsActive()),
                            r != null,
                            r == null ? 0 : noteRepository.countByMedicalRecord(r),
                            r == null ? 0 : accessRepository.countByMedicalRecordAndStatus(r, RecordAccessStatusEnum.PENDING),
                            r == null ? null : r.getUpdatedAt());
                })
                .sorted(Comparator.comparing(MedicalRecordSummaryDTO::linkActive).reversed()
                        .thenComparing(MedicalRecordSummaryDTO::patientName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Override
    @Transactional
    public MedicalRecordDTO get(User psychologist, UUID treatmentLinkId) {
        TreatmentLink link = ownedLink(psychologist, treatmentLinkId);
        support.ensureAvailable();
        Optional<MedicalRecord> record = recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link);
        if (record.isEmpty()) {
            return new MedicalRecordDTO(null, link.getId(), link.getPatient().getUser().getId(), patientName(link), active(link), false, active(link),
                    null, null, null, null, null, null, null, null, null);
        }
        MedicalRecord r = record.get();
        audit.record(psychologist, ClinicalAuditActionEnum.RECORD_VIEWED, r.getId(), r.getId());
        return toRecordDTO(r, link);
    }

    @Override
    @Transactional
    public MedicalRecordDTO update(User psychologist, UUID treatmentLinkId, MedicalRecordUpdateDTO dto) {
        TreatmentLink link = ownedLink(psychologist, treatmentLinkId);
        ensureActive(link, "O vínculo foi encerrado: o prontuário está disponível apenas para consulta e adendos.");

        boolean created = false;
        MedicalRecord record = recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link).orElse(null);
        if (record == null) {
            record = support.createRecord(link);
            created = true;
        } else if (dto.version() != null && !dto.version().equals(record.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(MedicalRecord.class, record.getId());
        }

        record.setDemandEncrypted(support.encrypt(record, recordField(record, "demand"), TextSanitizer.clean(dto.demand())));
        record.setGoalsEncrypted(support.encrypt(record, recordField(record, "goals"), TextSanitizer.clean(dto.goals())));
        record.setHistoryEncrypted(support.encrypt(record, recordField(record, "history"), TextSanitizer.clean(dto.history())));
        record.setProceduresEncrypted(support.encrypt(record, recordField(record, "procedures"), TextSanitizer.clean(dto.procedures())));
        record.setClosingEncrypted(support.encrypt(record, recordField(record, "closing"), TextSanitizer.clean(dto.closing())));
        record = recordRepository.saveAndFlush(record);

        audit.record(psychologist, created ? ClinicalAuditActionEnum.RECORD_CREATED : ClinicalAuditActionEnum.RECORD_UPDATED,
                record.getId(), record.getId());
        return toRecordDTO(record, link);
    }

    // ------------------------------------------------------------------ anotações

    @Override
    @Transactional
    public List<SessionNoteDTO> listNotes(User psychologist, UUID treatmentLinkId) {
        TreatmentLink link = ownedLink(psychologist, treatmentLinkId);
        support.ensureAvailable();
        return recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link)
                .map(record -> {
                    audit.record(psychologist, ClinicalAuditActionEnum.NOTE_VIEWED, record.getId(), "list");
                    return noteRepository.findByMedicalRecordOrderByCreatedAtDesc(record).stream()
                            .map(n -> support.toNoteDTO(n, isEditable(n, psychologist), true))
                            .toList();
                })
                .orElse(List.of());
    }

    @Override
    @Transactional
    public SessionNoteDTO createNote(User psychologist, UUID treatmentLinkId, SessionNoteCreateDTO dto) {
        TreatmentLink link = ownedLink(psychologist, treatmentLinkId);
        ensureActive(link, "O vínculo foi encerrado: não é possível criar novas anotações.");
        RichTextSanitizer.SanitizedDocument doc = richText.sanitize(dto.content());

        MedicalRecord record = recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link)
                .orElseGet(() -> support.createRecord(link));

        SessionNote note = new SessionNote();
        note.setMedicalRecord(record);
        note.setType(dto.type());
        note.setStatus(SessionNoteStatusEnum.DRAFT);
        note.setAuthor(userRepository.getReferenceById(psychologist.getId()));
        if (dto.appointmentId() != null) {
            Appointment appointment = appointmentRepository.findById(dto.appointmentId())
                    .filter(a -> a.getTreatmentLink() != null && a.getTreatmentLink().getId().equals(link.getId()))
                    .orElseThrow(() -> new BusinessException("appointmentId", "A consulta informada não pertence a este paciente."));
            note.setAppointment(appointment);
        }
        note = noteRepository.saveAndFlush(note); // precisa do id para o contexto da criptografia

        note.setTitleEncrypted(support.encrypt(record, noteField(note, "title"), TextSanitizer.cleanLine(dto.title())));
        note.setContentEncrypted(support.encrypt(record, noteField(note, "content"), doc.canonicalJson()));
        note = noteRepository.saveAndFlush(note);

        audit.record(psychologist, ClinicalAuditActionEnum.NOTE_CREATED, record.getId(), note.getId());
        return support.toNoteDTO(note, true, false);
    }

    @Override
    @Transactional
    public SessionNoteDTO getNote(User psychologist, UUID noteId) {
        SessionNote note = ownedNote(psychologist, noteId);
        audit.record(psychologist, ClinicalAuditActionEnum.NOTE_VIEWED, note.getMedicalRecord().getId(), note.getId());
        return support.toNoteDTO(note, isEditable(note, psychologist), true);
    }

    @Override
    @Transactional
    public SessionNoteDTO saveDraft(User psychologist, UUID noteId, SessionNoteDraftUpdateDTO dto) {
        SessionNote note = ownedNote(psychologist, noteId);
        ensureDraftOfAuthor(note, psychologist);
        ensureActive(note.getMedicalRecord().getTreatmentLink(), "O vínculo foi encerrado: rascunhos não podem mais ser editados.");
        if (dto.version() != null && !dto.version().equals(note.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(SessionNote.class, note.getId());
        }

        RichTextSanitizer.SanitizedDocument doc = richText.sanitize(dto.content());
        MedicalRecord record = note.getMedicalRecord();
        note.setType(dto.type());
        note.setTitleEncrypted(support.encrypt(record, noteField(note, "title"), TextSanitizer.cleanLine(dto.title())));
        note.setContentEncrypted(support.encrypt(record, noteField(note, "content"), doc.canonicalJson()));
        note = noteRepository.saveAndFlush(note);

        audit.record(psychologist, ClinicalAuditActionEnum.NOTE_DRAFT_SAVED, record.getId(), note.getId());
        return support.toNoteDTO(note, true, false);
    }

    @Override
    @Transactional
    public SessionNoteDTO finalizeNote(User psychologist, UUID noteId) {
        SessionNote note = ownedNote(psychologist, noteId);
        ensureDraftOfAuthor(note, psychologist);
        MedicalRecord record = note.getMedicalRecord();

        String title = support.decrypt(record, noteField(note, "title"), note.getTitleEncrypted());
        String content = support.decrypt(record, noteField(note, "content"), note.getContentEncrypted());
        if (content == null || richText.sanitize(richText.parse(content)).isBlank()) {
            throw new BusinessException("content", "Não é possível finalizar uma anotação vazia.");
        }

        Psychologist profile = psychologistRepository.findByUserId(psychologist.getId())
                .orElseThrow(() -> new AccessDeniedException("Perfil de psicólogo não encontrado."));
        note.setAuthorName(psychologist.getFullName());
        note.setAuthorCrp(profile.getCrp());
        // Data/hora do SERVIDOR (truncada em micros para o hash ser reproduzível após reler do banco)
        note.setFinalizedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        note.setStatus(SessionNoteStatusEnum.FINALIZED);
        note.setIntegrityHash(noteHash(note, title, content));
        note = noteRepository.saveAndFlush(note);

        audit.record(psychologist, ClinicalAuditActionEnum.NOTE_FINALIZED, record.getId(), note.getId());
        return support.toNoteDTO(note, false, true);
    }

    @Override
    @Transactional
    public SessionNoteDTO addAddendum(User psychologist, UUID noteId, SessionNoteAddendumCreateDTO dto) {
        SessionNote note = ownedNote(psychologist, noteId);
        if (note.getStatus() != SessionNoteStatusEnum.FINALIZED) {
            throw new BusinessException("note", "Rascunhos podem ser editados diretamente; adendos são para anotações finalizadas.");
        }
        String reason = TextSanitizer.clean(dto.reason());
        if (reason == null || reason.isBlank()) throw new BusinessException("reason", "Informe o motivo da retificação.");
        RichTextSanitizer.SanitizedDocument doc = richText.sanitize(dto.content());
        if (doc.isBlank()) throw new BusinessException("content", "O adendo não pode ser vazio.");

        MedicalRecord record = note.getMedicalRecord();
        List<SessionNoteAddendum> existing = addendumRepository.findByNoteOrderBySequenceAsc(note);
        String previousHash = existing.isEmpty() ? note.getIntegrityHash() : existing.get(existing.size() - 1).getIntegrityHash();
        Psychologist profile = psychologistRepository.findByUserId(psychologist.getId())
                .orElseThrow(() -> new AccessDeniedException("Perfil de psicólogo não encontrado."));

        SessionNoteAddendum addendum = new SessionNoteAddendum();
        addendum.setNote(note);
        addendum.setSequence(existing.size() + 1);
        addendum.setAuthor(userRepository.getReferenceById(psychologist.getId()));
        addendum.setAuthorName(psychologist.getFullName());
        addendum.setAuthorCrp(profile.getCrp());
        addendum.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        addendum.setReasonEncrypted(support.encrypt(record, addendumField(note, addendum.getSequence(), "reason"), reason));
        addendum.setContentEncrypted(support.encrypt(record, addendumField(note, addendum.getSequence(), "content"), doc.canonicalJson()));
        addendum.setIntegrityHash(addendumHash(previousHash, addendum, reason, doc.canonicalJson()));
        addendumRepository.saveAndFlush(addendum);

        audit.record(psychologist, ClinicalAuditActionEnum.NOTE_ADDENDUM_ADDED, record.getId(), note.getId());
        return support.toNoteDTO(note, false, true);
    }

    // ------------------------------------------------------------------ solicitações do paciente

    @Override
    @Transactional(readOnly = true)
    public List<AccessRequestDTO> listAccessRequests(User psychologist) {
        ensurePsychologist(psychologist);
        return accessRepository.findByPsychologistOrderByRequestedAtDesc(psychologist).stream()
                .map(r -> support.toAccessDTO(r, true))
                .toList();
    }

    @Override
    @Transactional
    public AccessRequestDTO grant(User psychologist, UUID requestId, AccessGrantDTO dto) {
        RecordAccessRequest request = ownedPendingRequest(psychologist, requestId);
        if (dto.expiresAt() != null && dto.expiresAt().isAfter(LocalDateTime.now().plusDays(MAX_GRANT_DAYS))) {
            throw new BusinessException("expiresAt", "A validade máxima da liberação é de " + MAX_GRANT_DAYS + " dias.");
        }
        request.setStatus(RecordAccessStatusEnum.GRANTED);
        request.setDecidedAt(LocalDateTime.now());
        request.setExpiresAt(dto.expiresAt());
        request.setExportAllowed(dto.exportAllowed());
        accessRepository.save(request);

        audit.record(psychologist, ClinicalAuditActionEnum.ACCESS_GRANTED, request.getMedicalRecord().getId(), request.getId());
        User patient = request.getPatient();
        notificationService.create(patient, psychologist, NotificationType.MEDICAL_RECORD_ACCESS_GRANTED,
                "Acesso ao prontuário liberado",
                "Seu(sua) psicólogo(a) liberou o acesso ao seu prontuário para consulta.",
                "MEDICAL_RECORD", request.getId());
        emailService.sendRecordAccessDecided(patient.getEmail(), patient.getFullName(), true);
        return support.toAccessDTO(request, true);
    }

    @Override
    @Transactional
    public AccessRequestDTO deny(User psychologist, UUID requestId, AccessDenyDTO dto) {
        RecordAccessRequest request = ownedPendingRequest(psychologist, requestId);
        String justification = TextSanitizer.clean(dto.justification());
        if (justification == null || justification.length() < 10) {
            throw new BusinessException("justification", "A justificativa deve ter ao menos 10 caracteres.");
        }
        MedicalRecord record = request.getMedicalRecord();
        request.setStatus(RecordAccessStatusEnum.DENIED);
        request.setDecidedAt(LocalDateTime.now());
        request.setJustificationEncrypted(support.encrypt(record, "access:" + request.getId() + ":justification", justification));
        accessRepository.save(request);

        audit.record(psychologist, ClinicalAuditActionEnum.ACCESS_DENIED, record.getId(), request.getId());
        User patient = request.getPatient();
        // A justificativa NÃO vai na notificação nem no e-mail — só na plataforma
        notificationService.create(patient, psychologist, NotificationType.MEDICAL_RECORD_ACCESS_DENIED,
                "Solicitação de prontuário respondida",
                "Seu(sua) psicólogo(a) respondeu à sua solicitação de acesso ao prontuário.",
                "MEDICAL_RECORD", request.getId());
        emailService.sendRecordAccessDecided(patient.getEmail(), patient.getFullName(), false);
        return support.toAccessDTO(request, true);
    }

    // ------------------------------------------------------------------ exportação

    @Override
    @Transactional
    public ExportedPdfDTO exportPdf(User psychologist, UUID treatmentLinkId, boolean includePrivateNotes) {
        TreatmentLink link = ownedLink(psychologist, treatmentLinkId);
        support.ensureAvailable();
        MedicalRecord r = recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link)
                .orElseThrow(() -> new BusinessException("record", "O prontuário deste paciente ainda não foi aberto."));

        // Ordem cronológica de finalização; rascunhos nunca entram no documento
        List<SessionNoteDTO> notes = noteRepository.findByMedicalRecordOrderByCreatedAtDesc(r).stream()
                .filter(n -> n.getStatus() == SessionNoteStatusEnum.FINALIZED)
                .filter(n -> includePrivateNotes || n.getType() == com.psiconet.model.enums.clinical.SessionNoteTypeEnum.RECORD)
                .sorted(Comparator.comparing(SessionNote::getFinalizedAt))
                .map(n -> support.toNoteDTO(n, false, true))
                .toList();

        PatientRecordViewDTO view = new PatientRecordViewDTO(
                null,
                patientName(link),
                link.getPsychologist().getUser().getFullName(),
                link.getPsychologist().getCrp(),
                r.getOpeningDate(),
                support.decrypt(r, recordField(r, "demand"), r.getDemandEncrypted()),
                support.decrypt(r, recordField(r, "goals"), r.getGoalsEncrypted()),
                support.decrypt(r, recordField(r, "history"), r.getHistoryEncrypted()),
                support.decrypt(r, recordField(r, "procedures"), r.getProceduresEncrypted()),
                support.decrypt(r, recordField(r, "closing"), r.getClosingEncrypted()),
                notes, null, true);

        UUID exportId = UUID.randomUUID();
        byte[] pdf = pdfService.render(view, exportId, MedicalRecordPdfService.Audience.PSYCHOLOGIST, includePrivateNotes);
        String sha256 = ClinicalAuditService.sha256Bytes(pdf);
        // alvo (máx. 64): id da exportação + escopo (C = completo com anotações pessoais, D = documental) + início do hash
        audit.record(psychologist, ClinicalAuditActionEnum.PDF_EXPORTED, r.getId(),
                exportId + (includePrivateNotes ? ":C:" : ":D:") + sha256.substring(0, 16));

        String suffix = includePrivateNotes ? "-completo" : "-documental";
        return new ExportedPdfDTO(pdf, sha256, "prontuario" + suffix + "-" + exportId.toString().substring(0, 8) + ".pdf");
    }

    // ------------------------------------------------------------------ auditoria

    @Override
    @Transactional
    public Page<ClinicalAuditEntryDTO> auditTrail(User psychologist, UUID treatmentLinkId, Pageable pageable) {
        TreatmentLink link = ownedLink(psychologist, treatmentLinkId);
        MedicalRecord record = recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link)
                .orElseThrow(() -> new EntityNotFoundException(MedicalRecord.class, treatmentLinkId));
        audit.record(psychologist, ClinicalAuditActionEnum.AUDIT_VIEWED, record.getId(), record.getId());

        Page<ClinicalAuditLog> page = auditRepository.findByMedicalRecordIdOrderBySequenceDesc(record.getId(), pageable);
        Set<UUID> actorIds = page.stream().map(ClinicalAuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return page.map(e -> new ClinicalAuditEntryDTO(e.getSequence(), e.getAction(), e.getActorRole(),
                e.getActorId() == null ? "Sistema" : names.getOrDefault(e.getActorId(), "—"), e.getOccurredAt()));
    }

    // ------------------------------------------------------------------ guarda de acesso

    private void ensurePsychologist(User user) {
        if (user.getRole() != RoleEnum.PSYCHOLOGIST) {
            throw new AccessDeniedException("Somente psicólogos acessam prontuários.");
        }
    }

    private TreatmentLink ownedLink(User psychologist, UUID linkId) {
        ensurePsychologist(psychologist);
        TreatmentLink link = linkRepository.findWithPartiesById(linkId)
                .orElseThrow(() -> new EntityNotFoundException(TreatmentLink.class, linkId));
        if (!link.getPsychologist().getUser().getId().equals(psychologist.getId())) {
            recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link).ifPresent(r ->
                    audit.record(psychologist, ClinicalAuditActionEnum.ACCESS_DENIED_ATTEMPT, r.getId(), linkId));
            throw new EntityNotFoundException(TreatmentLink.class, linkId);
        }
        return link;
    }

    private SessionNote ownedNote(User psychologist, UUID noteId) {
        ensurePsychologist(psychologist);
        support.ensureAvailable();
        SessionNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new EntityNotFoundException(SessionNote.class, noteId));
        MedicalRecord record = note.getMedicalRecord();
        if (record == null || !record.getTreatmentLink().getPsychologist().getUser().getId().equals(psychologist.getId())) {
            if (record != null) audit.record(psychologist, ClinicalAuditActionEnum.ACCESS_DENIED_ATTEMPT, record.getId(), noteId);
            throw new EntityNotFoundException(SessionNote.class, noteId);
        }
        return note;
    }

    private RecordAccessRequest ownedPendingRequest(User psychologist, UUID requestId) {
        ensurePsychologist(psychologist);
        support.ensureAvailable();
        RecordAccessRequest request = accessRepository.findByIdAndPsychologist(requestId, psychologist)
                .orElseThrow(() -> new EntityNotFoundException(RecordAccessRequest.class, requestId));
        if (request.getStatus() != RecordAccessStatusEnum.PENDING) {
            throw new BusinessException("status", "Esta solicitação já foi respondida.");
        }
        return request;
    }

    private void ensureDraftOfAuthor(SessionNote note, User psychologist) {
        if (note.getStatus() == SessionNoteStatusEnum.FINALIZED) {
            throw new BusinessException("note", "Anotação finalizada é imutável. Registre um adendo para corrigir ou complementar.");
        }
        if (note.getAuthor() != null && !note.getAuthor().getId().equals(psychologist.getId())) {
            throw new EntityNotFoundException(SessionNote.class, note.getId());
        }
    }

    private static void ensureActive(TreatmentLink link, String message) {
        if (!active(link)) throw new BusinessException("treatmentLink", message);
    }

    private static boolean active(TreatmentLink link) {
        return Boolean.TRUE.equals(link.getIsActive());
    }

    private static boolean isEditable(SessionNote note, User psychologist) {
        return note.getStatus() == SessionNoteStatusEnum.DRAFT
                && (note.getAuthor() == null || note.getAuthor().getId().equals(psychologist.getId()))
                && active(note.getMedicalRecord().getTreatmentLink());
    }

    private static String patientName(TreatmentLink link) {
        return link.getPatient().getUser().getFullName();
    }

    // ------------------------------------------------------------------ mapeamento

    private MedicalRecordDTO toRecordDTO(MedicalRecord r, TreatmentLink link) {
        return new MedicalRecordDTO(
                r.getId(), link.getId(), link.getPatient().getUser().getId(), patientName(link), active(link), true, active(link), r.getOpeningDate(),
                support.decrypt(r, recordField(r, "demand"), r.getDemandEncrypted()),
                support.decrypt(r, recordField(r, "goals"), r.getGoalsEncrypted()),
                support.decrypt(r, recordField(r, "history"), r.getHistoryEncrypted()),
                support.decrypt(r, recordField(r, "procedures"), r.getProceduresEncrypted()),
                support.decrypt(r, recordField(r, "closing"), r.getClosingEncrypted()),
                r.getCreatedAt(), r.getUpdatedAt(), r.getVersion());
    }


}
