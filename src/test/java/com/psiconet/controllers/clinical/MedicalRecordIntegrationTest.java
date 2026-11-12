package com.psiconet.controllers.clinical;

import tools.jackson.databind.JsonNode;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.services.implement.clinical.ClinicalAuditService;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MedicalRecordIntegrationTest extends IntegrationTestBase {

    @Autowired TreatmentLinkRepository linkRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClinicalAuditService clinicalAudit;

    private User psychologist;
    private User patient;
    private TreatmentLink link;
    private String psyToken;
    private String patientToken;

    private static final String SECRET = "Paciente relata crises de pânico no trabalho";

    @BeforeEach
    void setUp() throws Exception {
        psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        TreatmentLink l = new TreatmentLink();
        l.setPsychologist(psychologistRepository.findByUserId(psychologist.getId()).orElseThrow());
        l.setPatient(patientRepository.findByUserId(patient.getId()).orElseThrow());
        l.setIsActive(true);
        l.setStartDate(LocalDate.now());
        link = linkRepository.save(l);
        psyToken = tokenFor(psychologist);
        patientToken = tokenFor(patient);
    }

    private static Map<String, Object> doc(String text) {
        return Map.of("type", "doc", "content", List.of(
                Map.of("type", "heading", "attrs", Map.of("level", 1), "content", List.of(Map.of("type", "text", "text", "Sessão"))),
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text, "marks", List.of(Map.of("type", "bold")))))));
    }

    private JsonNode createNote(String type, String text) throws Exception {
        return body(mvc.perform(post("/medical-records/links/" + link.getId() + "/notes").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", type, "title", "Evolução", "content", doc(text)))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String finalizeNote(String noteId) throws Exception {
        return body(mvc.perform(post("/medical-records/notes/" + noteId + "/finalize").header("Authorization", bearer(psyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.integrityHash", matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.integrityVerified").value(true))
                .andReturn()).get("id").asString();
    }

    @Test
    void psicologoTitularRegistraProntuarioCifrado() throws Exception {
        mvc.perform(put("/medical-records/links/" + link.getId()).header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("demand", SECRET, "goals", "Reduzir crises"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.demand").value(SECRET));

        // Nada clínico em texto claro no banco
        List<String> raw = jdbc.queryForList(
                "select demanda_cifrada from prontuario where paciente_psicologo_id = ?", String.class, link.getId());
        assertThat(raw).singleElement().satisfies(v -> assertThat(v).startsWith("g1:").doesNotContain("pânico"));

        mvc.perform(get("/medical-records/links/" + link.getId()).header("Authorization", bearer(psyToken)))
                .andExpect(jsonPath("$.goals").value("Reduzir crises"));
    }

    @Test
    void outrosUsuariosNaoAcessamProntuario() throws Exception {
        String otherPsy = tokenFor(createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE));
        String admin = tokenFor(createUser(RoleEnum.ADMIN, UserStatusEnum.ACTIVE));

        mvc.perform(get("/medical-records/links/" + link.getId()).header("Authorization", bearer(otherPsy)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/medical-records/links/" + link.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/medical-records").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/patient/medical-records").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotacaoRascunhoFinalizacaoImutavelEAdendo() throws Exception {
        JsonNode note = createNote("RECORD", SECRET);
        String id = note.get("id").asString();

        // autosave do rascunho
        mvc.perform(put("/medical-records/notes/" + id + "/draft").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "RECORD", "title", "Evolução", "content", doc(SECRET + " (rev)"),
                                "version", note.get("version").asLong()))))
                .andExpect(status().isOk());

        String raw = jdbc.queryForObject("select conteudo_cifrado from anotacoes_sessao where id = ?", String.class,
                java.util.UUID.fromString(id));
        assertThat(raw).startsWith("g1:").doesNotContain("pânico");

        finalizeNote(id);

        // Imutável depois de finalizada
        mvc.perform(put("/medical-records/notes/" + id + "/draft").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "RECORD", "content", doc("alterado")))))
                .andExpect(status().isBadRequest());

        // Correção por adendo assinado, encadeado ao hash original
        mvc.perform(post("/medical-records/notes/" + id + "/addenda").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Retificação de data", "content", doc("Correção: sessão de terça")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addenda", hasSize(1)))
                .andExpect(jsonPath("$.addenda[0].sequence").value(1))
                .andExpect(jsonPath("$.addenda[0].integrityVerified").value(true))
                .andExpect(jsonPath("$.integrityVerified").value(true));

        assertThat(clinicalAudit.verifyChain()).isTrue();
    }

    @Test
    void conteudoForaDaWhitelistEhRecusado() throws Exception {
        Map<String, Object> malicious = Map.of("type", "doc", "content", List.of(
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", "x",
                        "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", "javascript:alert(1)"))))))));
        mvc.perform(post("/medical-records/links/" + link.getId() + "/notes").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "RECORD", "content", malicious))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void fluxoDeAcessoDoPaciente() throws Exception {
        mvc.perform(put("/medical-records/links/" + link.getId()).header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("demand", SECRET)))).andExpect(status().isOk());
        String recordNote = createNote("RECORD", "Registro documental da sessão").get("id").asString();
        finalizeNote(recordNote);
        String privateNote = createNote("PRIVATE", "Hipótese pessoal do profissional").get("id").asString();
        finalizeNote(privateNote);
        createNote("RECORD", "Rascunho não finalizado");

        // Paciente solicita (sem acesso automático)
        String requestId = body(mvc.perform(post("/patient/medical-records/links/" + link.getId() + "/access-requests")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()).get("id").asString();

        mvc.perform(post("/patient/medical-records/links/" + link.getId() + "/access-requests").header("Authorization", bearer(patientToken)))
                .andExpect(status().isConflict());
        mvc.perform(get("/patient/medical-records/access-requests/" + requestId + "/record").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());

        // Psicólogo é notificado (sem conteúdo clínico nem nome do paciente)
        mvc.perform(get("/notifications").header("Authorization", bearer(psyToken)))
                .andExpect(jsonPath("$[0].type").value("MEDICAL_RECORD_ACCESS_REQUESTED"))
                .andExpect(jsonPath("$[0].message", not(containsString(patient.getFullName()))));

        // Libera somente leitura, com exportação
        mvc.perform(post("/medical-records/access-requests/" + requestId + "/grant").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("exportAllowed", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"));

        String view = mvc.perform(get("/patient/medical-records/access-requests/" + requestId + "/record")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.demand").value(SECRET))
                .andExpect(jsonPath("$.notes", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(view).doesNotContain("Hipótese pessoal").doesNotContain("Rascunho não finalizado");

        byte[] pdf = mvc.perform(get("/patient/medical-records/access-requests/" + requestId + "/pdf")
                        .header("Authorization", bearer(patientToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Document-SHA256", matchesPattern("[0-9a-f]{64}")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        // Outro paciente não acessa a liberação de terceiros
        String otherPatient = tokenFor(createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE));
        mvc.perform(get("/patient/medical-records/access-requests/" + requestId + "/record").header("Authorization", bearer(otherPatient)))
                .andExpect(status().isNotFound());

        assertThat(clinicalAudit.verifyChain()).isTrue();
    }

    @Test
    void negativaExigeJustificativaQueNaoVaiNaNotificacao() throws Exception {
        String requestId = body(mvc.perform(post("/patient/medical-records/links/" + link.getId() + "/access-requests")
                .header("Authorization", bearer(patientToken))).andExpect(status().isCreated()).andReturn()).get("id").asString();

        mvc.perform(post("/medical-records/access-requests/" + requestId + "/deny").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("justification", "curta"))))
                .andExpect(status().isBadRequest());

        String justification = "Conteúdo em elaboração clínica; discutiremos na próxima sessão.";
        mvc.perform(post("/medical-records/access-requests/" + requestId + "/deny").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("justification", justification))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DENIED"));

        mvc.perform(get("/notifications").header("Authorization", bearer(patientToken)))
                .andExpect(jsonPath("$[0].type").value("MEDICAL_RECORD_ACCESS_DENIED"))
                .andExpect(jsonPath("$[0].message", not(containsString("elaboração"))));

        // A justificativa fica disponível ao titular dentro da plataforma
        mvc.perform(get("/patient/medical-records").header("Authorization", bearer(patientToken)))
                .andExpect(jsonPath("$[0].latestRequest.justification").value(justification));
    }

    @Test
    void vinculoEncerradoMantemGuardaMasBloqueiaNovasAnotacoes() throws Exception {
        mvc.perform(put("/medical-records/links/" + link.getId()).header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("demand", SECRET)))).andExpect(status().isOk());
        link.setIsActive(false);
        linkRepository.save(link);

        mvc.perform(get("/medical-records/links/" + link.getId()).header("Authorization", bearer(psyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.demand").value(SECRET));
        mvc.perform(post("/medical-records/links/" + link.getId() + "/notes").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "RECORD", "content", doc("x")))))
                .andExpect(status().isBadRequest());
        // paciente continua podendo solicitar acesso
        mvc.perform(post("/patient/medical-records/links/" + link.getId() + "/access-requests").header("Authorization", bearer(patientToken)))
                .andExpect(status().isCreated());
    }

    private String pdfText(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
        com.lowagie.text.pdf.parser.PdfTextExtractor extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) sb.append(extractor.getTextFromPage(i)).append('\n');
        reader.close();
        return sb.toString();
    }

    @Test
    void psicologoExportaPdfComOuSemAnotacoesPessoais() throws Exception {
        mvc.perform(put("/medical-records/links/" + link.getId()).header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("demand", "Demanda documental")))).andExpect(status().isOk());
        finalizeNote(createNote("RECORD", "Registro de evolucao visivel").get("id").asString());
        finalizeNote(createNote("PRIVATE", "Hipotese pessoal reservada").get("id").asString());
        createNote("RECORD", "Rascunho nunca exportado");

        byte[] documental = mvc.perform(get("/medical-records/links/" + link.getId() + "/pdf").header("Authorization", bearer(psyToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Document-SHA256", matchesPattern("[0-9a-f]{64}")))
                .andReturn().getResponse().getContentAsByteArray();
        String textoDocumental = pdfText(documental);
        assertThat(textoDocumental).contains("Registro de evolucao visivel").doesNotContain("Hipotese pessoal").doesNotContain("Rascunho");

        byte[] completo = mvc.perform(get("/medical-records/links/" + link.getId() + "/pdf").param("includePrivateNotes", "true")
                        .header("Authorization", bearer(psyToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String textoCompleto = pdfText(completo);
        assertThat(textoCompleto).contains("Registro de evolucao visivel").contains("Hipotese pessoal reservada")
                .contains("Anotação pessoal").doesNotContain("Rascunho");

        // Só o titular exporta; paciente e outro psicólogo não
        String otherPsy = tokenFor(createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE));
        mvc.perform(get("/medical-records/links/" + link.getId() + "/pdf").header("Authorization", bearer(otherPsy)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/medical-records/links/" + link.getId() + "/pdf").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        assertThat(clinicalAudit.verifyChain()).isTrue();
    }

    @Test
    void anotacaoComAlinhamentoETamanhoMantemIntegridade() throws Exception {
        Map<String, Object> styled = Map.of("type", "doc", "content", List.of(
                Map.of("type", "paragraph", "attrs", Map.of("textAlign", "center"), "content", List.of(
                        Map.of("type", "text", "text", "Centralizado grande",
                                "marks", List.of(Map.of("type", "textStyle", "attrs", Map.of("fontSize", "20px"))))))));
        String id = body(mvc.perform(post("/medical-records/links/" + link.getId() + "/notes").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "RECORD", "content", styled))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content.content[0].attrs.textAlign").value("center"))
                .andExpect(jsonPath("$.content.content[0].content[0].marks[0].attrs.fontSize").value("20px"))
                .andReturn()).get("id").asString();
        finalizeNote(id);
        mvc.perform(get("/medical-records/links/" + link.getId() + "/pdf").header("Authorization", bearer(psyToken)))
                .andExpect(status().isOk());
    }
}
