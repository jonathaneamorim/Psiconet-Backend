package com.psiconet.services.implement.clinical;

import tools.jackson.databind.JsonNode;
import com.psiconet.infra.crypto.ClinicalCryptoService;
import com.psiconet.infra.crypto.ClinicalCryptoService.WrappedKey;
import com.psiconet.infra.exceptions.ClinicalUnavailableException;
import com.psiconet.infra.richtext.RichTextSanitizer;
import com.psiconet.model.dtos.clinical.record.AccessRequestDTO;
import com.psiconet.model.dtos.clinical.record.SessionNoteAddendumDTO;
import com.psiconet.model.entities.clinical.RecordAccessRequest;
import com.psiconet.model.dtos.clinical.record.SessionNoteDTO;
import com.psiconet.model.entities.clinical.MedicalRecord;
import com.psiconet.model.entities.clinical.SessionNote;
import com.psiconet.model.entities.clinical.SessionNoteAddendum;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.clinical.SessionNoteStatusEnum;
import com.psiconet.repositories.clinical.MedicalRecordRepository;
import com.psiconet.repositories.clinical.SessionNoteAddendumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operações compartilhadas entre a visão do psicólogo e a do paciente: abrir/criar prontuário com
 * chave de dados própria, cifrar/decifrar campos com contexto (AAD) e montar DTOs de anotações com
 * verificação de integridade. Todo texto claro existe só em memória durante a requisição.
 */
@Component
@RequiredArgsConstructor
public class ClinicalRecordSupport {

    private final ClinicalCryptoService crypto;
    private final RichTextSanitizer richText;
    private final MedicalRecordRepository recordRepository;
    private final SessionNoteAddendumRepository addendumRepository;

    public void ensureAvailable() {
        if (!crypto.isAvailable()) throw new ClinicalUnavailableException();
    }

    /** Cria o prontuário do vínculo (um por vínculo), com nova chave de dados. */
    public MedicalRecord createRecord(TreatmentLink link) {
        ensureAvailable();
        MedicalRecord record = new MedicalRecord();
        record.setTreatmentLink(link);
        record.setOpeningDate(LocalDate.now());
        WrappedKey key = crypto.newDataKey();
        record.setWrappedDataKey(key.wrappedKey());
        record.setKeyVersion(key.keyVersion());
        return recordRepository.saveAndFlush(record);
    }

    public WrappedKey keyOf(MedicalRecord record) {
        if (record.getWrappedDataKey() == null) {
            // Prontuário legado sem chave (criado antes da criptografia): gera e grava agora
            WrappedKey key = crypto.newDataKey();
            record.setWrappedDataKey(key.wrappedKey());
            record.setKeyVersion(key.keyVersion());
            recordRepository.save(record);
            return key;
        }
        return new WrappedKey(record.getWrappedDataKey(), record.getKeyVersion());
    }

    public String encrypt(MedicalRecord record, String context, String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        return crypto.encryptField(keyOf(record), context, plaintext);
    }

    public String decrypt(MedicalRecord record, String context, String stored) {
        if (stored == null) return null;
        return crypto.decryptField(keyOf(record), context, stored);
    }

    // ---- contextos (AAD): amarram o texto cifrado ao registro e ao campo ----

    public static String recordField(MedicalRecord record, String field) {
        return "record:" + record.getId() + ":" + field;
    }

    public static String noteField(SessionNote note, String field) {
        return "note:" + note.getId() + ":" + field;
    }

    public static String addendumField(SessionNote note, int sequence, String field) {
        return "addendum:" + note.getId() + ":" + sequence + ":" + field;
    }

    // ---- integridade ----

    public static String noteHash(SessionNote note, String title, String canonicalContent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("noteId", String.valueOf(note.getId()));
        payload.put("recordId", String.valueOf(note.getMedicalRecord().getId()));
        payload.put("appointmentId", note.getAppointment() == null ? null : String.valueOf(note.getAppointment().getId()));
        payload.put("type", String.valueOf(note.getType()));
        payload.put("title", title);
        payload.put("content", canonicalContent);
        payload.put("authorName", note.getAuthorName());
        payload.put("authorCrp", note.getAuthorCrp());
        payload.put("finalizedAt", String.valueOf(note.getFinalizedAt()));
        return ClinicalAuditService.sha256(RichTextSanitizer.canonical(
                tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(payload)));
    }

    public static String addendumHash(String previousHash, SessionNoteAddendum a, String reason, String canonicalContent) {
        return ClinicalAuditService.sha256(String.join("|",
                String.valueOf(previousHash),
                String.valueOf(a.getNote().getId()),
                String.valueOf(a.getSequence()),
                reason,
                canonicalContent,
                a.getAuthorName(),
                String.valueOf(a.getAuthorCrp()),
                String.valueOf(a.getCreatedAt())));
    }

    // ---- montagem de DTOs ----

    public SessionNoteDTO toNoteDTO(SessionNote note, boolean editable, boolean includeAddenda) {
        MedicalRecord record = note.getMedicalRecord();
        String title = decrypt(record, noteField(note, "title"), note.getTitleEncrypted());
        String contentJson = decrypt(record, noteField(note, "content"), note.getContentEncrypted());
        JsonNode content = contentJson == null ? null : richText.parse(contentJson);

        boolean verified = false;
        if (note.getStatus() == SessionNoteStatusEnum.FINALIZED && note.getIntegrityHash() != null && contentJson != null) {
            verified = note.getIntegrityHash().equals(noteHash(note, title, contentJson));
        }

        List<SessionNoteAddendumDTO> addenda = includeAddenda && note.getStatus() == SessionNoteStatusEnum.FINALIZED
                ? addendaOf(note)
                : List.of();

        return new SessionNoteDTO(
                note.getId(),
                note.getAppointment() == null ? null : note.getAppointment().getId(),
                note.getAppointment() == null ? null : note.getAppointment().getStartDateTime(),
                note.getType(), note.getStatus(), title, content,
                note.getAuthorName(), note.getAuthorCrp(), note.getIntegrityHash(), verified,
                note.getCreatedAt(), note.getUpdatedAt(), note.getFinalizedAt(),
                editable, note.getVersion(), addenda);
    }

    public List<SessionNoteAddendumDTO> addendaOf(SessionNote note) {
        MedicalRecord record = note.getMedicalRecord();
        String previous = note.getIntegrityHash();
        List<SessionNoteAddendum> list = addendumRepository.findByNoteOrderBySequenceAsc(note);
        java.util.ArrayList<SessionNoteAddendumDTO> out = new java.util.ArrayList<>();
        for (SessionNoteAddendum a : list) {
            String reason = decrypt(record, addendumField(note, a.getSequence(), "reason"), a.getReasonEncrypted());
            String content = decrypt(record, addendumField(note, a.getSequence(), "content"), a.getContentEncrypted());
            boolean verified = a.getIntegrityHash().equals(addendumHash(previous, a, reason, content));
            out.add(new SessionNoteAddendumDTO(a.getId(), a.getSequence(), reason, richText.parse(content),
                    a.getAuthorName(), a.getAuthorCrp(), a.getIntegrityHash(), verified, a.getCreatedAt()));
            previous = a.getIntegrityHash();
        }
        return out;
    }

    public AccessRequestDTO toAccessDTO(RecordAccessRequest r, boolean includeJustification) {
        MedicalRecord record = r.getMedicalRecord();
        String justification = includeJustification && r.getJustificationEncrypted() != null
                ? decrypt(record, "access:" + r.getId() + ":justification", r.getJustificationEncrypted())
                : null;
        return new AccessRequestDTO(r.getId(), record.getTreatmentLink().getId(),
                r.getPatient().getFullName(), r.getPsychologist().getFullName(), r.getStatus(),
                r.isActiveGrant(java.time.LocalDateTime.now()), r.getRequestedAt(), r.getDecidedAt(), r.getExpiresAt(),
                r.isExportAllowed(), justification);
    }}
