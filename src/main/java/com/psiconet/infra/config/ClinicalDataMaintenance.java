package com.psiconet.infra.config;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.psiconet.infra.crypto.ClinicalCryptoService;
import com.psiconet.infra.crypto.MasterKeyProvider;
import com.psiconet.infra.richtext.RichTextSanitizer;
import com.psiconet.model.entities.clinical.MedicalRecord;
import com.psiconet.model.entities.clinical.SessionNote;
import com.psiconet.model.enums.clinical.ClinicalAuditActionEnum;
import com.psiconet.repositories.clinical.MedicalRecordRepository;
import com.psiconet.repositories.clinical.SessionNoteRepository;
import com.psiconet.services.implement.clinical.ClinicalAuditService;
import com.psiconet.services.implement.clinical.ClinicalRecordSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.psiconet.services.implement.clinical.ClinicalRecordSupport.noteField;
import static com.psiconet.services.implement.clinical.ClinicalRecordSupport.recordField;

/**
 * Manutenção idempotente dos dados clínicos na inicialização (só com chave-mestra configurada):
 * <ol>
 *   <li><b>Migração do legado</b>: prontuários e anotações gravados em TEXTO PURO (colunas antigas)
 *       são cifrados no novo formato e só então as colunas antigas são esvaziadas — na mesma
 *       transação, sem perda. Anotações legadas viram rascunhos estruturados (título por campo).</li>
 *   <li><b>Rotação de chave-mestra</b>: DEKs protegidas por versão antiga são re-cifradas com a versão
 *       ativa (os dados em si não mudam).</li>
 * </ol>
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class ClinicalDataMaintenance implements CommandLineRunner {

    private final ClinicalCryptoService crypto;
    private final MasterKeyProvider keyProvider;
    private final MedicalRecordRepository recordRepository;
    private final SessionNoteRepository noteRepository;
    private final ClinicalRecordSupport support;
    private final ClinicalAuditService audit;
    private final RichTextSanitizer richText;

    @Override
    @Transactional
    public void run(String... args) {
        if (!crypto.isAvailable()) return;
        int records = migrateLegacyRecords();
        int notes = migrateLegacyNotes();
        int rewrapped = rewrapOldKeys();
        if (records + notes + rewrapped > 0) {
            log.info("Manutenção clínica: {} prontuário(s) e {} anotação(ões) legados cifrados; {} chave(s) re-cifrada(s).",
                    records, notes, rewrapped);
        }
    }

    int migrateLegacyRecords() {
        int count = 0;
        for (MedicalRecord r : recordRepository.findAll()) {
            boolean legacy = r.getMainComplaint() != null || r.getFamilyHistory() != null
                    || r.getMedicalHistory() != null || r.getTherapeuticGoals() != null;
            if (!legacy) continue;

            if (r.getDemandEncrypted() == null) r.setDemandEncrypted(support.encrypt(r, recordField(r, "demand"), r.getMainComplaint()));
            if (r.getGoalsEncrypted() == null) r.setGoalsEncrypted(support.encrypt(r, recordField(r, "goals"), r.getTherapeuticGoals()));
            if (r.getHistoryEncrypted() == null) {
                String history = join("Histórico familiar", r.getFamilyHistory(), "Antecedentes médicos", r.getMedicalHistory());
                r.setHistoryEncrypted(support.encrypt(r, recordField(r, "history"), history));
            }
            r.setMainComplaint(null);
            r.setFamilyHistory(null);
            r.setMedicalHistory(null);
            r.setTherapeuticGoals(null);
            recordRepository.save(r);
            audit.record(null, ClinicalAuditActionEnum.LEGACY_DATA_MIGRATED, r.getId(), r.getId());
            count++;
        }
        return count;
    }

    int migrateLegacyNotes() {
        Set<SessionNote> legacy = new LinkedHashSet<>();
        legacy.addAll(noteRepository.findByContentEncryptedIsNullAndSummaryIsNotNull());
        legacy.addAll(noteRepository.findByContentEncryptedIsNullAndMentalStateIsNotNull());
        legacy.addAll(noteRepository.findByContentEncryptedIsNullAndHomeworkIsNotNull());
        int count = 0;
        for (SessionNote note : legacy) {
            if (note.getMedicalRecord() == null) {
                if (note.getAppointment() == null || note.getAppointment().getTreatmentLink() == null) {
                    log.warn("Anotação legada {} sem consulta/vínculo: mantida como está para revisão manual.", note.getId());
                    continue;
                }
                var link = note.getAppointment().getTreatmentLink();
                note.setMedicalRecord(recordRepository.findFirstByTreatmentLinkOrderByCreatedAtAsc(link)
                        .orElseGet(() -> support.createRecord(link)));
            }
            ObjectNode doc = legacyDocument(note);
            String canonical = richText.sanitize(doc).canonicalJson();
            note.setContentEncrypted(support.encrypt(note.getMedicalRecord(), noteField(note, "content"), canonical));
            note.setTitleEncrypted(support.encrypt(note.getMedicalRecord(), noteField(note, "title"), "Anotação migrada do formato anterior"));
            note.setSummary(null);
            note.setMentalState(null);
            note.setHomework(null);
            noteRepository.save(note);
            audit.record(null, ClinicalAuditActionEnum.LEGACY_DATA_MIGRATED, note.getMedicalRecord().getId(), note.getId());
            count++;
        }
        return count;
    }

    int rewrapOldKeys() {
        int count = 0;
        for (MedicalRecord r : recordRepository.findByKeyVersionNot(keyProvider.activeVersion())) {
            if (r.getWrappedDataKey() == null) continue;
            var rewrapped = crypto.rewrap(new ClinicalCryptoService.WrappedKey(r.getWrappedDataKey(), r.getKeyVersion()));
            r.setWrappedDataKey(rewrapped.wrappedKey());
            r.setKeyVersion(rewrapped.keyVersion());
            recordRepository.save(r);
            audit.record(null, ClinicalAuditActionEnum.KEY_REWRAPPED, r.getId(), rewrapped.keyVersion());
            count++;
        }
        return count;
    }

    private static ObjectNode legacyDocument(SessionNote note) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode doc = f.objectNode().put("type", "doc");
        ArrayNode content = doc.putArray("content");
        addSection(content, "Resumo", note.getSummary());
        addSection(content, "Estado mental", note.getMentalState());
        addSection(content, "Tarefas de casa", note.getHomework());
        return doc;
    }

    private static void addSection(ArrayNode content, String title, String text) {
        if (text == null || text.isBlank()) return;
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode heading = content.addObject().put("type", "heading");
        heading.putObject("attrs").put("level", 2);
        heading.putArray("content").addObject().put("type", "text").put("text", title);
        for (String line : text.split("\\R")) {
            ObjectNode p = content.addObject().put("type", "paragraph");
            if (!line.isBlank()) p.putArray("content").add(f.objectNode().put("type", "text").put("text", line));
        }
    }

    private static String join(String label1, String v1, String label2, String v2) {
        StringBuilder sb = new StringBuilder();
        if (v1 != null && !v1.isBlank()) sb.append(label1).append(":\n").append(v1);
        if (v2 != null && !v2.isBlank()) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(label2).append(":\n").append(v2);
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
