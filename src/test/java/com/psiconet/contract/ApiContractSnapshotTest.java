package com.psiconet.contract;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.financial.Payment;
import com.psiconet.model.entities.profile.conection.Connection;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import com.psiconet.model.enums.conection.ConectionStatusEnum;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.repositories.financial.PaymentRepository;
import com.psiconet.repositories.profile.ConnectionRepository;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.support.IntegrationTestBase;
import com.psiconet.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Contrato JSON da API consumido pelo frontend: chaves, tipos e FORMATO dos valores (data local × instante
 * com Z, escala de decimais, estrutura da página) dos endpoints principais. Protege trocas de versão do
 * Spring/Jackson (ADR 018): qualquer diferença é bug, salvo decisão registrada.
 *
 * Atualizar a referência de propósito: {@code ./mvnw test -Dtest=ApiContractSnapshotTest -Dcontract.update=true}.
 */
class ApiContractSnapshotTest extends IntegrationTestBase {

    private static final Path SNAPSHOT = Path.of("src/test/resources/contract/api-contract.json");

    private static final Pattern UUID_RE = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern DATE_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern LOCAL_DATETIME_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?$");
    private static final Pattern INSTANT_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
    private static final Pattern OFFSET_DATETIME_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?[+-]\\d{2}:\\d{2}$");
    private static final Pattern TIME_RE = Pattern.compile("^\\d{2}:\\d{2}(:\\d{2})?$");

    @Autowired TreatmentLinkRepository linkRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ConnectionRepository connectionRepository;
    @Autowired NotificationService notificationService;
    @Autowired com.psiconet.repositories.clinical.RecurrenceRuleRepository ruleRepository;

    @Test
    void contratoDaApiPermaneceIgual() throws Exception {
        User psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        User admin = createUser(RoleEnum.ADMIN, UserStatusEnum.ACTIVE);

        TreatmentLink link = new TreatmentLink();
        link.setPsychologist(psychologistRepository.findByUserId(psychologist.getId()).orElseThrow());
        link.setPatient(patientRepository.findByUserId(patient.getId()).orElseThrow());
        link.setIsActive(true);
        link.setStartDate(LocalDate.now());
        link.setDefaultPrice(new BigDecimal("150.00"));
        link = linkRepository.save(link);

        Appointment appointment = new Appointment();
        appointment.setTreatmentLink(link);
        appointment.setTitle("Sessão");
        appointment.setStartDateTime(LocalDateTime.now().plusDays(2).withNano(0));
        appointment.setEndDateTime(appointment.getStartDateTime().plusMinutes(50));
        appointment.setMeetingType(MeetingTypeEnum.VIDEO_CALL);
        appointment.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
        appointment.setMeetingLink("https://meet.example.com/x");
        appointment.setStatus(AppointmentStatusEnum.ACCEPTED);
        appointment.setPrice(new BigDecimal("150.00"));
        appointment = appointmentRepository.save(appointment);

        Payment payment = new Payment();
        payment.setTreatmentLink(link);
        payment.setAppointment(appointment);
        payment.setAmount(new BigDecimal("150.00"));
        payment.setStatus(PaymentStatusEnum.PENDING);
        paymentRepository.save(payment);

        Connection connection = new Connection();
        connection.setSender(patient);
        connection.setReceiver(psychologist);
        connection.setStatus(ConectionStatusEnum.ACCEPTED);
        connection.setRequestedAt(LocalDateTime.now().minusDays(1));
        connection.setAcceptedAt(LocalDateTime.now());
        connectionRepository.save(connection);

        notificationService.create(patient, psychologist, NotificationType.APPOINTMENT_SCHEDULED,
                "Consulta agendada", "Sua consulta foi agendada.", "APPOINTMENT", appointment.getId());

        Map<String, JsonNode> contract = new TreeMap<>();
        MvcResult login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", psychologist.getEmail(), "password", TestData.STRONG_PASSWORD)))).andReturn();
        contract.put("POST /auth/login", shape(login));
        String psy = body(login).get("token").asString();
        String pat = tokenFor(patient);
        String adm = tokenFor(admin);

        record(contract, "GET /users/me (psicólogo)", get("/users/me"), psy);
        record(contract, "GET /psychologists/me", get("/psychologists/me"), psy);
        record(contract, "GET /patients/me", get("/patients/me"), pat);
        record(contract, "GET /psychologists/{id}", get("/psychologists/" + psychologist.getId()), pat);
        record(contract, "GET /appointments (psicólogo)", get("/appointments"), psy);
        record(contract, "GET /appointments (paciente)", get("/appointments"), pat);
        record(contract, "GET /appointments/{id}", get("/appointments/" + appointment.getId()), psy);
        record(contract, "GET /appointments/stats", get("/appointments/stats").param("year", "2026").param("month", "1"), psy);
        record(contract, "GET /payments", get("/payments"), pat);
        record(contract, "GET /payments/summary", get("/payments/summary"), psy);
        record(contract, "GET /connections", get("/connections"), psy);
        record(contract, "GET /notifications", get("/notifications"), pat);
        record(contract, "GET /notifications/unread-count", get("/notifications/unread-count"), pat);
        record(contract, "GET /notifications/preferences", get("/notifications/preferences"), pat);
        record(contract, "GET /tickets", get("/tickets"), pat);
        record(contract, "GET /legal/versions", get("/legal/versions"), null);
        record(contract, "GET /legal/consents/status", get("/legal/consents/status"), pat);
        record(contract, "GET /video/config", get("/video/config"), pat);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setTreatmentLink(link);
        rule.setFrequency(RecurrenceFrequencyEnum.WEEKLY);
        rule.setDayOfWeek(java.time.DayOfWeek.MONDAY);
        rule.setStartTime(java.time.LocalTime.of(10, 0));
        rule.setEndTime(java.time.LocalTime.of(10, 50));
        rule.setStartDate(LocalDate.now().plusDays(1));
        rule.setMeetingType(MeetingTypeEnum.VIDEO_CALL);
        rule.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
        rule.setMeetingLink("https://meet.example.com/x");
        rule.setPrice(new BigDecimal("150.00"));
        rule.setIsActive(true);
        rule = ruleRepository.save(rule);
        record(contract, "GET /treatment-links/{id}/recurrence-rules", get("/treatment-links/" + link.getId() + "/recurrence-rules"), psy);
        record(contract, "GET /recurrence-rules/{id}/preview", get("/recurrence-rules/" + rule.getId() + "/preview")
                .param("from", LocalDate.now().toString()).param("to", LocalDate.now().plusDays(20).toString()), psy);

        // Prontuário: conteúdo do editor (JsonNode) atravessa o Jackson na entrada e na saída
        record(contract, "GET /medical-records/links/{id}", get("/medical-records/links/" + link.getId()), psy);
        MvcResult note = mvc.perform(post("/medical-records/links/" + link.getId() + "/notes").header("Authorization", bearer(psy))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"RECORD\",\"title\":\"Sessão\",\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Relato\",\"marks\":[{\"type\":\"bold\"}]}]}]}}"))
                .andReturn();
        contract.put("POST /medical-records/links/{id}/notes", shape(note));
        record(contract, "GET /medical-records", get("/medical-records"), psy);
        record(contract, "GET /patient/medical-records", get("/patient/medical-records"), pat);
        record(contract, "GET /admin/users", get("/admin/users"), adm);
        record(contract, "GET /admin/dashboard", get("/admin/dashboard"), adm);
        record(contract, "erro 401", get("/notifications"), null);
        record(contract, "erro 404", get("/appointments/" + java.util.UUID.randomUUID()), psy);
        record(contract, "erro 400 validação", post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content("{}"), null);

        ObjectNode actual = objectMapper.createObjectNode();
        contract.forEach(actual::set);
        String text = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n";

        if (Boolean.getBoolean("contract.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, text, StandardCharsets.UTF_8);
            return;
        }
        assertEquals(Files.readString(SNAPSHOT, StandardCharsets.UTF_8), text,
                "Contrato JSON mudou. Se for intencional, registre a decisão e rode com -Dcontract.update=true");
    }

    private void record(Map<String, JsonNode> contract, String name, MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) request.header("Authorization", bearer(token));
        contract.put(name, shape(mvc.perform(request).andReturn()));
    }

    private JsonNode shape(MvcResult result) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("status", result.getResponse().getStatus());
        node.put("contentType", String.valueOf(result.getResponse().getContentType()));
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        node.set("body", text.isEmpty() ? objectMapper.nullNode() : signature(objectMapper.readTree(text)));
        return node;
    }

    /** Estrutura do JSON: valores trocados pelo tipo/formato (estável entre execuções). */
    private JsonNode signature(JsonNode value) {
        if (value.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonNode> e : value.properties()) {
                sorted.put(e.getKey(), signature(e.getValue()));
            }
            sorted.forEach(out::set);
            return out;
        }
        if (value.isArray()) {
            var out = objectMapper.createArrayNode();
            if (!value.isEmpty()) out.add(signature(value.get(0)));
            return out;
        }
        if (value.isNull()) return objectMapper.getNodeFactory().stringNode("null");
        if (value.isBoolean()) return objectMapper.getNodeFactory().stringNode("boolean");
        if (value.isNumber()) {
            String raw = value.toString();
            int dot = raw.indexOf('.');
            return objectMapper.getNodeFactory().stringNode(dot < 0 ? "integer" : "decimal(" + (raw.length() - dot - 1) + ")");
        }
        String s = value.asString();
        String kind;
        if (UUID_RE.matcher(s).matches()) kind = "uuid";
        else if (DATE_RE.matcher(s).matches()) kind = "date";
        else if (INSTANT_RE.matcher(s).matches()) kind = "instant-utc";
        else if (OFFSET_DATETIME_RE.matcher(s).matches()) kind = "datetime-offset";
        else if (LOCAL_DATETIME_RE.matcher(s).matches()) kind = "datetime-local";
        else if (TIME_RE.matcher(s).matches()) kind = "time";
        else kind = "string";
        return objectMapper.getNodeFactory().stringNode(kind);
    }
}
