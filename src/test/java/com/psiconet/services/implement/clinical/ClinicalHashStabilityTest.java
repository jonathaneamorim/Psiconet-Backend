package com.psiconet.services.implement.clinical;

import tools.jackson.databind.json.JsonMapper;
import com.psiconet.infra.richtext.RichTextSanitizer;
import com.psiconet.model.entities.clinical.MedicalRecord;
import com.psiconet.model.entities.clinical.SessionNote;
import com.psiconet.model.enums.clinical.SessionNoteTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hash de integridade do prontuário é recalculado a cada leitura de anotação finalizada: a serialização
 * canônica NÃO pode mudar entre versões do Jackson/Spring (ADR 018), senão registros antigos aparecem
 * como adulterados. Valores de referência gravados com Spring Boot 3.3/3.5 (Jackson 2) — nunca altere.
 */
class ClinicalHashStabilityTest {

    static final String DOC = """
            {"type":"doc","content":[
              {"type":"heading","attrs":{"level":2,"textAlign":"center"},"content":[{"type":"text","text":"Sessão nº 3 — ação","marks":[{"type":"textStyle","attrs":{"fontSize":"18px"}}]}]},
              {"type":"paragraph","attrs":{"textAlign":"right"},"content":[{"type":"text","text":"Relato \\"entre aspas\\" ç ã 😊 <b>x</b>","marks":[{"type":"italic"},{"type":"bold"}]},{"type":"hardBreak"},{"type":"text","text":"fim"}]},
              {"type":"orderedList","attrs":{"start":3},"content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"um","marks":[{"type":"underline"}]}]}]}]}
            ]}""";

    static final String EXPECTED_CANONICAL =
            "{\"content\":[{\"attrs\":{\"level\":2,\"textAlign\":\"center\"},\"content\":[{\"marks\":[{\"attrs\":{\"fontSize\":\"18px\"},\"type\":\"textStyle\"}],\"text\":\"Sessão nº 3 — ação\",\"type\":\"text\"}],\"type\":\"heading\"},{\"attrs\":{\"textAlign\":\"right\"},\"content\":[{\"marks\":[{\"type\":\"bold\"},{\"type\":\"italic\"}],\"text\":\"Relato \\\"entre aspas\\\" ç ã 😊 <b>x</b>\",\"type\":\"text\"},{\"type\":\"hardBreak\"},{\"text\":\"fim\",\"type\":\"text\"}],\"type\":\"paragraph\"},{\"attrs\":{\"start\":3},\"content\":[{\"content\":[{\"content\":[{\"marks\":[{\"type\":\"underline\"}],\"text\":\"um\",\"type\":\"text\"}],\"type\":\"paragraph\"}],\"type\":\"listItem\"}],\"type\":\"orderedList\"}],\"type\":\"doc\"}";
    static final String EXPECTED_NOTE_HASH = "322e5f6bb524dfc89787b201efa66189b16bc5453886aea0b02b0e4a543c26a9";

    @Test
    void canonicalizacaoEHashDaAnotacaoNaoMudam() throws Exception {
        var sanitized = new RichTextSanitizer().sanitize(JsonMapper.builder().build().readTree(DOC));

        SessionNote note = new SessionNote();
        note.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        MedicalRecord record = new MedicalRecord();
        record.setId(UUID.fromString("66666666-7777-8888-9999-000000000000"));
        note.setMedicalRecord(record);
        note.setType(SessionNoteTypeEnum.RECORD);
        note.setAuthorName("Dra. Ana Lúcia");
        note.setAuthorCrp("06/123456");
        note.setFinalizedAt(LocalDateTime.of(2026, 11, 12, 9, 30, 15, 123456000));
        String hash = ClinicalRecordSupport.noteHash(note, "Título — sessão", sanitized.canonicalJson());

        assertThat(sanitized.canonicalJson()).isEqualTo(EXPECTED_CANONICAL);
        assertThat(hash).isEqualTo(EXPECTED_NOTE_HASH);
    }
}
