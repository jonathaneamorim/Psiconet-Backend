package com.psiconet.controllers;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.financial.Payment;
import com.psiconet.model.entities.profile.conection.Connection;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import com.psiconet.model.enums.conection.ConectionStatusEnum;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.repositories.financial.PaymentRepository;
import com.psiconet.repositories.profile.ConnectionRepository;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * SEC-13 — matriz de autorização: recurso de outro usuário ⇒ 404 (não revela existência);
 * participante do recurso sem a permissão do papel ⇒ 403. Ver docs/seguranca/matriz-de-autorizacao.md.
 */
class AuthorizationMatrixIntegrationTest extends IntegrationTestBase {

    @Autowired TreatmentLinkRepository linkRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired RecurrenceRuleRepository ruleRepository;
    @Autowired ConnectionRepository connectionRepository;

    private User psychologist;
    private User patient;
    private String psyToken;
    private String patientToken;
    private String outsiderPsyToken;
    private String outsiderPatientToken;

    private TreatmentLink link;
    private Appointment appointment;
    private Payment payment;
    private RecurrenceRule rule;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        User outsiderPsy = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        User outsiderPatient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);

        TreatmentLink l = new TreatmentLink();
        l.setPsychologist(psychologistRepository.findByUserId(psychologist.getId()).orElseThrow());
        l.setPatient(patientRepository.findByUserId(patient.getId()).orElseThrow());
        l.setIsActive(true);
        l.setStartDate(LocalDate.now());
        l.setDefaultPrice(new BigDecimal("150.00"));
        link = linkRepository.save(l);

        Appointment a = new Appointment();
        a.setTreatmentLink(link);
        a.setTitle("Sessão");
        a.setStartDateTime(LocalDateTime.now().plusDays(3));
        a.setEndDateTime(LocalDateTime.now().plusDays(3).plusMinutes(50));
        a.setMeetingType(MeetingTypeEnum.VIDEO_CALL);
        a.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
        a.setMeetingLink("https://meet.example.com/x");
        a.setStatus(AppointmentStatusEnum.ACCEPTED);
        a.setPrice(new BigDecimal("150.00"));
        appointment = appointmentRepository.save(a);

        Payment p = new Payment();
        p.setTreatmentLink(link);
        p.setAppointment(appointment);
        p.setAmount(new BigDecimal("150.00"));
        p.setStatus(PaymentStatusEnum.AWAITING_REVIEW);
        p.setReceiptUrl("receipts/inexistente.pdf");
        payment = paymentRepository.save(p);

        RecurrenceRule r = new RecurrenceRule();
        r.setTreatmentLink(link);
        r.setFrequency(RecurrenceFrequencyEnum.WEEKLY);
        r.setDayOfWeek(DayOfWeek.MONDAY);
        r.setStartTime(LocalTime.of(10, 0));
        r.setEndTime(LocalTime.of(10, 50));
        r.setStartDate(LocalDate.now().plusDays(1));
        r.setMeetingType(MeetingTypeEnum.VIDEO_CALL);
        r.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
        r.setMeetingLink("https://meet.example.com/x");
        r.setIsActive(true);
        rule = ruleRepository.save(r);

        Connection c = new Connection();
        c.setSender(patient);
        c.setReceiver(psychologist);
        c.setStatus(ConectionStatusEnum.PENDING);
        c.setRequestedAt(LocalDateTime.now());
        connection = connectionRepository.save(c);

        psyToken = tokenFor(psychologist);
        patientToken = tokenFor(patient);
        outsiderPsyToken = tokenFor(outsiderPsy);
        outsiderPatientToken = tokenFor(outsiderPatient);
    }

    // ------------------------------------------------------------ recurso de terceiros ⇒ 404

    @Test
    void vinculoDeOutroPsicologo() throws Exception {
        expect(404, as(patch("/treatment-links/" + link.getId() + "/price"), outsiderPsyToken, Map.of("defaultPrice", 10)));
        expect(404, as(get("/treatment-links/" + link.getId() + "/recurrence-rules"), outsiderPatientToken, null));
    }

    @Test
    void regraDeRecorrenciaDeTerceiros() throws Exception {
        expect(404, as(patch("/recurrence-rules/" + rule.getId() + "/deactivate"), outsiderPsyToken, null));
        expect(404, as(get("/recurrence-rules/" + rule.getId() + "/preview")
                .param("from", LocalDate.now().toString()).param("to", LocalDate.now().plusDays(30).toString()), outsiderPatientToken, null));
    }

    @Test
    void pagamentoDeTerceiros() throws Exception {
        expect(404, as(get("/payments/" + payment.getId()), outsiderPatientToken, null));
        expect(404, as(get("/payments/" + payment.getId() + "/receipt"), outsiderPsyToken, null));
        expect(404, as(patch("/payments/" + payment.getId() + "/approve"), outsiderPsyToken, null));
        expect(404, as(post("/payments/" + payment.getId() + "/dispute"), outsiderPatientToken, Map.of("message", "Não reconheço")));
        expect(404, mvcMultipart("/payments/" + payment.getId() + "/receipt", outsiderPatientToken));
    }

    @Test
    void agendamentoDeTerceiros() throws Exception {
        expect(404, as(get("/appointments/" + appointment.getId()), outsiderPatientToken, null));
        expect(404, as(patch("/appointments/" + appointment.getId() + "/cancel"), outsiderPsyToken, Map.of("reason", "x")));
    }

    @Test
    void conexaoDeTerceiros() throws Exception {
        expect(404, as(patch("/connections/" + connection.getId() + "/accept"), outsiderPsyToken, null));
        expect(404, as(delete("/connections/" + connection.getId()), outsiderPatientToken, null));
    }

    // ------------------------------------------------------------ participante sem permissão do papel ⇒ 403

    @Test
    void participanteSemPermissaoDoPapelRecebe403() throws Exception {
        expect(403, as(patch("/payments/" + payment.getId() + "/approve"), patientToken, null));
        expect(403, as(patch("/recurrence-rules/" + rule.getId() + "/deactivate"), patientToken, null));
        // Quem enviou a solicitação não pode aceitá-la
        expect(403, as(patch("/connections/" + connection.getId() + "/accept"), patientToken, null));
        expect(403, as(post("/payments/" + payment.getId() + "/dispute"), psyToken, Map.of("message", "x")));
    }

    @Test
    void papelErradoRecebe403() throws Exception {
        expect(403, as(patch("/treatment-links/" + link.getId() + "/price"), patientToken, Map.of("defaultPrice", 10)));
        expect(403, as(post("/appointments"), patientToken, Map.of("treatmentLinkId", link.getId().toString())));
    }

    // ------------------------------------------------------------ dono do recurso continua com acesso

    @Test
    void donoContinuaComAcesso() throws Exception {
        expect(200, as(get("/payments/" + payment.getId()), patientToken, null));
        expect(200, as(get("/appointments/" + appointment.getId()), psyToken, null));
        expect(200, as(get("/treatment-links/" + link.getId() + "/recurrence-rules"), patientToken, null));
    }

    // ------------------------------------------------------------ apoio

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        return request;
    }

    private RequestBuilder mvcMultipart(String path, String token) {
        return multipart(path)
                .file(new MockMultipartFile("file", "c.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                .header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private void expect(int status, RequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
    }
}
