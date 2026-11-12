package com.psiconet.controllers.clinical;

import com.psiconet.model.entities.Notification;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.repositories.NotificationRepository;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.services.implement.clinical.RecurrenceGenerationService;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Parte B / ADR 019: sobreposição de horário permitida com confirmação e sinalizada só ao psicólogo. */
class AppointmentOverlapIntegrationTest extends IntegrationTestBase {

    @Autowired TreatmentLinkRepository linkRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired RecurrenceRuleRepository ruleRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired RecurrenceGenerationService generationService;

    private User psychologist;
    private User patientA;
    private User patientB;
    private String psyToken;
    private String patientAToken;
    private TreatmentLink linkB;

    /** Segunda-feira daqui a ~2 semanas, às 10h: futuro, dia útil, mesmo resultado em qualquer dia da execução. */
    private final LocalDateTime slot = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atTime(10, 0);

    @BeforeEach
    void setUp() throws Exception {
        psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        patientA = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        patientB = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        patientB.setFullName("Bruna Sigilosa");
        patientB = userRepository.save(patientB);
        link(patientA);
        linkB = link(patientB);
        psyToken = tokenFor(psychologist);
        patientAToken = tokenFor(patientA);
    }

    private TreatmentLink link(User patient) {
        TreatmentLink l = new TreatmentLink();
        l.setPsychologist(psychologistRepository.findByUserId(psychologist.getId()).orElseThrow());
        l.setPatient(patientRepository.findByUserId(patient.getId()).orElseThrow());
        l.setIsActive(true);
        l.setStartDate(LocalDate.now());
        l.setDefaultPrice(new BigDecimal("150.00"));
        return linkRepository.save(l);
    }

    private Map<String, Object> body(User patient, LocalDateTime start, int minutes, Boolean acknowledge) {
        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patient.getId());
        body.put("startDateTime", start.toString());
        body.put("endDateTime", start.plusMinutes(minutes).toString());
        body.put("meetingType", "VIDEO_CALL");
        body.put("meetingLink", "https://meet.example.com/x");
        if (acknowledge != null) body.put("acknowledgeOverlap", acknowledge);
        return body;
    }

    private MvcResult create(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/appointments").header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andReturn();
    }

    private JsonNode createOk(Map<String, Object> body) throws Exception {
        MvcResult r = create(body);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private JsonNode getAs(String token, String path) throws Exception {
        return body(mvc.perform(get(path).header("Authorization", bearer(token))).andReturn());
    }

    // ------------------------------------------------------------------ agendamento único

    @Test
    void semSobreposicaoCriaNormalmente() throws Exception {
        JsonNode created = createOk(body(patientA, slot, 50, null));
        assertThat(created.get("hasOverlap").asBoolean()).isFalse();
        assertThat(created.get("overlappingAppointments")).isEmpty();
    }

    @Test
    void comSobreposicaoSemConfirmacaoResponde409ComConflitosENaoCria() throws Exception {
        JsonNode first = createOk(body(patientA, slot, 50, null));
        long before = appointmentRepository.count();

        MvcResult r = create(body(patientB, slot.plusMinutes(20), 50, false));

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        JsonNode error = body(r);
        assertThat(error.get("code").asString()).isEqualTo("SCHEDULE_OVERLAP");
        assertThat(error.get("message").asString()).contains("já tem consulta");
        JsonNode conflict = error.get("conflicts").get(0);
        assertThat(conflict.get("id").asString()).isEqualTo(first.get("id").asString());
        assertThat(conflict.get("patientName").asString()).isEqualTo(patientA.getFullName());
        assertThat(conflict.has("startDateTime")).isTrue();
        assertThat(conflict.has("endDateTime")).isTrue();
        assertThat(appointmentRepository.count()).isEqualTo(before);
    }

    @Test
    void comConfirmacaoCriaESinalizaOsDois() throws Exception {
        JsonNode first = createOk(body(patientA, slot, 50, null));
        JsonNode second = createOk(body(patientB, slot.plusMinutes(20), 50, true));

        assertThat(second.get("hasOverlap").asBoolean()).isTrue();
        assertThat(second.get("overlappingAppointments").get(0).get("id").asString()).isEqualTo(first.get("id").asString());

        JsonNode firstNow = getAs(psyToken, "/appointments/" + first.get("id").asString());
        assertThat(firstNow.get("hasOverlap").asBoolean()).isTrue();

        // Lista (lote) também sinaliza
        JsonNode page = getAs(psyToken, "/appointments?size=100");
        assertThat(page.get("content")).allSatisfy(a -> assertThat(a.get("hasOverlap").asBoolean()).isTrue());
    }

    @Test
    void mesmoPacienteSobrepostoContinuaBloqueadoMesmoConfirmando() throws Exception {
        createOk(body(patientA, slot, 50, null));
        MvcResult r = create(body(patientA, slot.plusMinutes(10), 50, true));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(r).get("errors").get("startDateTime").asString()).contains("paciente já tem consulta");
    }

    @Test
    void consultasEncostadasNaoSobrepoem() throws Exception {
        createOk(body(patientA, slot, 50, null));
        JsonNode next = createOk(body(patientB, slot.plusMinutes(50), 50, null));
        assertThat(next.get("hasOverlap").asBoolean()).isFalse();
    }

    @Test
    void canceladaNaoContaECancelarRemoveOAlerta() throws Exception {
        JsonNode first = createOk(body(patientA, slot, 50, null));
        JsonNode second = createOk(body(patientB, slot, 50, true));

        mvc.perform(patch("/appointments/" + second.get("id").asString() + "/cancel").header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "remarcar"))));

        assertThat(getAs(psyToken, "/appointments/" + first.get("id").asString()).get("hasOverlap").asBoolean()).isFalse();
        // A consulta cancelada de B não conta: B pode voltar ao horário (não é "mesmo paciente") e o
        // conflito listado é só o de A
        JsonNode again = createOk(body(patientB, slot, 50, true));
        assertThat(again.get("overlappingAppointments")).hasSize(1);
        assertThat(again.get("overlappingAppointments").get(0).get("id").asString()).isEqualTo(first.get("id").asString());
    }

    @Test
    void historicoConcluidoNaoGeraAlerta() throws Exception {
        JsonNode first = createOk(body(patientA, slot, 50, null));
        JsonNode second = createOk(body(patientB, slot, 50, true));
        Appointment done = appointmentRepository.findById(java.util.UUID.fromString(first.get("id").asString())).orElseThrow();
        done.setStatus(AppointmentStatusEnum.COMPLETED);
        appointmentRepository.save(done);

        assertThat(getAs(psyToken, "/appointments/" + second.get("id").asString()).get("hasOverlap").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------ privacidade

    @Test
    void pacienteNuncaVeCamposDeSobreposicao() throws Exception {
        JsonNode first = createOk(body(patientA, slot, 50, null));
        createOk(body(patientB, slot, 50, true));

        JsonNode own = getAs(patientAToken, "/appointments/" + first.get("id").asString());
        assertThat(own.has("hasOverlap")).isFalse();
        assertThat(own.has("overlappingAppointments")).isFalse();
        assertThat(own.toString()).doesNotContain(patientB.getFullName());

        JsonNode list = getAs(patientAToken, "/appointments");
        assertThat(list.get("content").get(0).has("hasOverlap")).isFalse();
        assertThat(list.toString()).doesNotContain(patientB.getFullName());
    }

    @Test
    void preChecagemSoParaOPsicologoDonoESemVazarDeOutros() throws Exception {
        createOk(body(patientA, slot, 50, null));
        String range = "?start=" + slot.plusMinutes(10) + "&end=" + slot.plusMinutes(60);

        JsonNode mine = getAs(psyToken, "/appointments/overlaps" + range);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("patientName").asString()).isEqualTo(patientA.getFullName());

        String otherPsy = tokenFor(createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE));
        assertThat(getAs(otherPsy, "/appointments/overlaps" + range)).isEmpty();

        assertThat(mvc.perform(get("/appointments/overlaps" + range).header("Authorization", bearer(patientAToken)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        // excludeId: ao remarcar, a própria consulta não conta
        String id = getAs(psyToken, "/appointments").get("content").get(0).get("id").asString();
        assertThat(getAs(psyToken, "/appointments/overlaps" + range + "&excludeId=" + id)).isEmpty();
    }

    // ------------------------------------------------------------------ concorrência

    @Test
    void criacoesSimultaneasDoMesmoPacienteNaoDuplicam() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(pool.submit(() -> {
                start.await();
                return create(body(patientA, slot, 50, true)).getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> c : calls) statuses.add(c.get());
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(200);
        long active = appointmentRepository.findAll().stream()
                .filter(a -> a.getTreatmentLink().getPatient().getUser().getId().equals(patientA.getId()))
                .filter(a -> a.getStatus() != AppointmentStatusEnum.CANCELLED).count();
        assertThat(active).isEqualTo(1);
    }

    // ------------------------------------------------------------------ recorrência

    private Map<String, Object> weeklyRule(LocalDate startDate, Boolean acknowledge) {
        Map<String, Object> body = new HashMap<>();
        body.put("frequency", "WEEKLY");
        body.put("dayOfWeek", startDate.getDayOfWeek().name());
        body.put("startTime", "10:00:00");
        body.put("endTime", "10:50:00");
        body.put("startDate", startDate.toString());
        body.put("meetingType", "VIDEO_CALL");
        body.put("meetingLink", "https://meet.example.com/x");
        if (acknowledge != null) body.put("acknowledgeOverlap", acknowledge);
        return body;
    }

    private MvcResult createRule(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/treatment-links/" + linkB.getId() + "/recurrence-rules").header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andReturn();
    }

    @Test
    void regraComPrimeiraOcorrenciaSobrepostaExigeConfirmacao() throws Exception {
        createOk(body(patientA, slot, 50, null));

        MvcResult refused = createRule(weeklyRule(slot.toLocalDate(), null));
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(refused).get("code").asString()).isEqualTo("SCHEDULE_OVERLAP");
        assertThat(ruleRepository.findAll()).noneMatch(r -> r.getTreatmentLink().getId().equals(linkB.getId()));

        MvcResult ok = createRule(weeklyRule(slot.toLocalDate(), true));
        assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(201);
        // A ocorrência foi gerada (não pulada) e aparece sinalizada
        JsonNode page = getAs(psyToken, "/appointments?size=100");
        assertThat(page.get("content")).hasSize(2).allSatisfy(a -> assertThat(a.get("hasOverlap").asBoolean()).isTrue());
    }

    @Test
    void ocorrenciaFuturaSobrepostaEGeradaENotificadaUmaVez() throws Exception {
        // Regra começa uma semana antes do horário ocupado: a 1ª ocorrência é livre, a 2ª coincide
        MvcResult ok = createRule(weeklyRule(slot.toLocalDate().minusWeeks(1), null));
        assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(201);
        RecurrenceRule rule = ruleRepository.findAll().stream()
                .filter(r -> r.getTreatmentLink().getId().equals(linkB.getId())).findFirst().orElseThrow();
        createOk(body(patientA, slot, 50, null));

        // 1ª ocorrência acontece (concluída) ⇒ o job materializa a próxima, que coincide com a consulta de A
        Appointment firstOccurrence = appointmentRepository.findAll().stream()
                .filter(a -> a.getRecurrenceRule() != null && a.getRecurrenceRule().getId().equals(rule.getId()))
                .findFirst().orElseThrow();
        firstOccurrence.setStatus(AppointmentStatusEnum.COMPLETED);
        appointmentRepository.save(firstOccurrence);

        generationService.ensureNextInstance(rule);
        generationService.ensureNextInstance(rule);

        List<Appointment> occurrences = appointmentRepository.findAll().stream()
                .filter(a -> a.getRecurrenceRule() != null && a.getRecurrenceRule().getId().equals(rule.getId()))
                .toList();
        assertThat(occurrences).hasSize(2).anyMatch(a -> a.getStartDateTime().equals(slot));

        List<Notification> overlapNotices = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(psychologist.getId()).stream()
                .filter(n -> n.getType() == NotificationType.SCHEDULE_OVERLAP).toList();
        assertThat(overlapNotices).hasSize(1);
        assertThat(overlapNotices.get(0).getMessage()).doesNotContain(patientA.getFullName()).doesNotContain(patientB.getFullName());
        assertThat(overlapNotices.get(0).getReferenceType()).isEqualTo("APPOINTMENT");
    }

    @Test
    void previewDaRegraEDoRascunhoMarcamDatasSobrepostas() throws Exception {
        createOk(body(patientA, slot.plusWeeks(1), 50, null));
        Map<String, Object> draft = weeklyRule(slot.toLocalDate(), null);

        JsonNode fromDraft = body(mvc.perform(post("/treatment-links/" + linkB.getId() + "/recurrence-rules/overlaps")
                .header("Authorization", bearer(psyToken)).contentType(MediaType.APPLICATION_JSON).content(json(draft))).andReturn());
        assertThat(fromDraft).hasSize(1);
        assertThat(fromDraft.get(0).get("startDateTime").asString()).startsWith(slot.plusWeeks(1).toLocalDate().toString());
        assertThat(fromDraft.get(0).get("overlappingAppointments").get(0).get("patientName").asString()).isEqualTo(patientA.getFullName());

        assertThat(createRule(weeklyRule(slot.toLocalDate(), null)).getResponse().getStatus()).isEqualTo(201);
        RecurrenceRule rule = ruleRepository.findAll().stream()
                .filter(r -> r.getTreatmentLink().getId().equals(linkB.getId())).findFirst().orElseThrow();
        String range = "?from=" + slot.toLocalDate() + "&to=" + slot.toLocalDate().plusWeeks(4);
        JsonNode marked = getAs(psyToken, "/recurrence-rules/" + rule.getId() + "/preview/overlaps" + range);
        assertThat(marked).hasSize(1);

        // O preview antigo (lista de datas) não muda de formato
        JsonNode plain = getAs(psyToken, "/recurrence-rules/" + rule.getId() + "/preview" + range);
        assertThat(plain.get(0).isString()).isTrue();

        // Paciente não acessa a marcação
        String patientBToken = tokenFor(patientB);
        assertThat(mvc.perform(get("/recurrence-rules/" + rule.getId() + "/preview/overlaps" + range)
                .header("Authorization", bearer(patientBToken))).andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
