package com.psiconet.controllers.clinical;

import com.fasterxml.jackson.databind.JsonNode;
import com.psiconet.infra.video.InvalidWebhookSignatureException;
import com.psiconet.infra.video.VideoProperties;
import com.psiconet.infra.video.VideoProvider;
import com.psiconet.infra.video.VideoWebhookEvent;
import com.psiconet.model.entities.Notification;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.clinical.AppointmentStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.VideoSessionEventEnum;
import com.psiconet.repositories.NotificationRepository;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.repositories.clinical.VideoSessionLogRepository;
import com.psiconet.services.implement.video.VideoRoomIds;
import com.psiconet.services.implement.video.VideoSessionReminderService;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Videochamada integrada (ADR 016): agendamento, autorização, janela, webhook e lembrete. Provedor mockado. */
class VideoSessionIntegrationTest extends IntegrationTestBase {

    @MockBean VideoProvider videoProvider;

    @Autowired TreatmentLinkRepository linkRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired VideoSessionLogRepository logRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired VideoSessionReminderService reminderService;
    @Autowired VideoProperties videoProperties;

    private User psychologist;
    private User patient;
    private User outsider;
    private TreatmentLink link;
    private String psyToken;
    private String patientToken;

    @BeforeEach
    void setUp() throws Exception {
        videoProperties.setEnabled(true);
        psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        outsider = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);

        TreatmentLink l = new TreatmentLink();
        l.setPsychologist(psychologistRepository.findByUserId(psychologist.getId()).orElseThrow());
        l.setPatient(patientRepository.findByUserId(patient.getId()).orElseThrow());
        l.setIsActive(true);
        l.setStartDate(LocalDate.now());
        l.setDefaultPrice(new BigDecimal("150.00"));
        link = linkRepository.save(l);

        psyToken = tokenFor(psychologist);
        patientToken = tokenFor(patient);

        when(videoProvider.name()).thenReturn("livekit");
        when(videoProvider.issueToken(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> new VideoProvider.IssuedToken("token-" + inv.getArgument(1), Instant.now().plusSeconds(600)));
    }

    // ---------------------------------------------------------------- agendamento

    private Map<String, Object> createBody(String provider, String link) {
        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patient.getId());
        body.put("startDateTime", LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MINUTES).toString());
        body.put("meetingType", "VIDEO_CALL");
        if (provider != null) body.put("meetingProvider", provider);
        if (link != null) body.put("meetingLink", link);
        return body;
    }

    private MvcResult postAppointment(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/appointments").header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andReturn();
    }

    @Test
    void consultaNaSalaPsiconetGeraSalaOpacaSemLinkENaoExpoeRoomId() throws Exception {
        MvcResult result = postAppointment(createBody("IN_APP", "https://ignorado.example.com"));
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = body(result);

        assertEquals("IN_APP", body.get("meetingProvider").asText());
        assertTrue(body.get("meetingLink").isNull());
        assertFalse(body.has("roomId"));
        assertFalse(body.get("joinOpensAt").isNull());
        assertFalse(body.get("joinClosesAt").isNull());

        Appointment saved = appointmentRepository.findById(UUID.fromString(body.get("id").asText())).orElseThrow();
        assertNotNull(saved.getRoomId());
        assertEquals(22, saved.getRoomId().length());
        assertFalse(saved.getRoomId().contains(psychologist.getId().toString()));
        assertFalse(result.getResponse().getContentAsString().contains(saved.getRoomId()));
    }

    @Test
    void provedorOmitidoMantemLinkExternoENormalizaEsquema() throws Exception {
        JsonNode body = body(postAppointment(createBody(null, "meet.google.com/abc-defg-hij")));
        assertEquals("EXTERNAL_LINK", body.get("meetingProvider").asText());
        assertEquals("https://meet.google.com/abc-defg-hij", body.get("meetingLink").asText());
        assertTrue(body.get("joinOpensAt").isNull());
    }

    @Test
    void linkExternoComEsquemaPerigosoOuSemLinkERecusado() throws Exception {
        for (String bad : List.of("javascript:alert(1)", "data:text/html,oi", "https://usuario@evil.example.com/x", " ")) {
            MvcResult result = postAppointment(createBody("EXTERNAL_LINK", bad));
            assertEquals(400, result.getResponse().getStatus(), bad);
            assertTrue(body(result).get("errors").has("meetingLink"), bad);
        }
        MvcResult tooLong = postAppointment(createBody("EXTERNAL_LINK", "https://x.example.com/" + "a".repeat(260)));
        assertEquals(400, tooLong.getResponse().getStatus());
    }

    @Test
    void salaPsiconetComFlagDesligadaERecusadaMasLinkExternoFunciona() throws Exception {
        videoProperties.setEnabled(false);
        try {
            MvcResult inApp = postAppointment(createBody("IN_APP", null));
            assertEquals(400, inApp.getResponse().getStatus());
            assertTrue(body(inApp).get("errors").has("meetingProvider"));

            assertEquals(200, postAppointment(createBody("EXTERNAL_LINK", "https://zoom.us/j/123")).getResponse().getStatus());

            mvc.perform(get("/video/config").header("Authorization", bearer(psyToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
        } finally {
            videoProperties.setEnabled(true);
        }
    }

    @Test
    void recorrenciaNaSalaPsiconetGeraSalaNovaEmCadaOcorrencia() throws Exception {
        LocalDate start = LocalDate.now().plusDays(1);
        Map<String, Object> rule = new HashMap<>();
        rule.put("frequency", "WEEKLY");
        rule.put("dayOfWeek", start.getDayOfWeek().name());
        rule.put("startTime", "14:00:00");
        rule.put("endTime", "14:50:00");
        rule.put("startDate", start.toString());
        rule.put("meetingType", "VIDEO_CALL");
        rule.put("meetingProvider", "IN_APP");
        rule.put("adjustForWeekend", false);

        mvc.perform(post("/treatment-links/" + link.getId() + "/recurrence-rules").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(rule)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.meetingProvider").value("IN_APP"));

        Appointment first = activeInstancesOfLink().get(0);
        assertNotNull(first.getRoomId());
        assertNull(first.getMeetingLink());

        // Cancela só esta ocorrência: a próxima é materializada com sala própria.
        mvc.perform(patch("/appointments/" + first.getId() + "/cancel").header("Authorization", bearer(psyToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("cancelScope", "SINGLE"))))
                .andExpect(status().isOk());

        Appointment second = activeInstancesOfLink().get(0);
        assertNotEquals(first.getId(), second.getId());
        assertNotNull(second.getRoomId());
        assertNotEquals(first.getRoomId(), second.getRoomId());
    }

    private List<Appointment> activeInstancesOfLink() {
        return appointmentRepository.findAll().stream()
                .filter(a -> a.getTreatmentLink().getId().equals(link.getId()))
                .filter(a -> a.getStatus() == AppointmentStatusEnum.ACCEPTED)
                .toList();
    }

    // ---------------------------------------------------------------- consulta e entrada

    private Appointment appointment(LocalDateTime start, int minutes, MeetingTypeEnum type, MeetingProviderEnum provider,
                                    AppointmentStatusEnum status) {
        Appointment a = new Appointment();
        a.setTreatmentLink(link);
        a.setTitle("Sessão");
        a.setStartDateTime(start);
        a.setEndDateTime(start.plusMinutes(minutes));
        a.setMeetingType(type);
        a.setStatus(status);
        a.setPrice(BigDecimal.TEN);
        if (type == MeetingTypeEnum.VIDEO_CALL) {
            a.setMeetingProvider(provider);
            if (provider == MeetingProviderEnum.IN_APP) a.setRoomId(VideoRoomIds.newRoomId());
            else a.setMeetingLink("https://meet.example.com/x");
        } else {
            Location location = new Location();
            location.setStreet("Rua A");
            location.setCity("São Paulo");
            location.setState("SP");
            a.setLocation(location);
        }
        return appointmentRepository.save(a);
    }

    private Appointment inAppStartingIn(long minutes) {
        return appointment(LocalDateTime.now().plusMinutes(minutes), 50, MeetingTypeEnum.VIDEO_CALL,
                MeetingProviderEnum.IN_APP, AppointmentStatusEnum.ACCEPTED);
    }

    private MvcResult join(Appointment a, String token) throws Exception {
        return mvc.perform(post("/appointments/" + a.getId() + "/video/join").header("Authorization", bearer(token))).andReturn();
    }

    @Test
    void getPorIdSoParaParticipantesESemRoomId() throws Exception {
        Appointment a = inAppStartingIn(60);
        MvcResult own = mvc.perform(get("/appointments/" + a.getId()).header("Authorization", bearer(patientToken))).andReturn();
        assertEquals(200, own.getResponse().getStatus());
        assertFalse(own.getResponse().getContentAsString().contains(a.getRoomId()));

        mvc.perform(get("/appointments/" + a.getId()).header("Authorization", bearer(tokenFor(outsider))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/appointments/" + UUID.randomUUID()).header("Authorization", bearer(psyToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void psicologoEntraComoAnfitriaoEPacienteComoConvidadoNaSalaDeDoisParticipantes() throws Exception {
        Appointment a = inAppStartingIn(5);

        MvcResult host = join(a, psyToken);
        assertEquals(200, host.getResponse().getStatus());
        JsonNode hostBody = body(host);
        assertEquals("HOST", hostBody.get("role").asText());
        assertEquals("token-" + psychologist.getId(), hostBody.get("token").asText());
        assertEquals(psychologist.getId().toString(), hostBody.get("identity").asText());
        assertEquals(patient.getId().toString(), hostBody.get("remoteIdentity").asText());
        assertEquals("wss://video.psiconet.test", hostBody.get("serverUrl").asText());
        assertEquals(15 * 60, hostBody.get("aloneTimeoutSeconds").asLong());
        // Limite de horário: 60 min após o fim da consulta
        assertEquals(a.getEndDateTime().plusMinutes(60).truncatedTo(ChronoUnit.SECONDS),
                LocalDateTime.parse(hostBody.get("hardStopAt").asText()).truncatedTo(ChronoUnit.SECONDS));
        assertEquals("no-store", host.getResponse().getHeader("Cache-Control"));
        assertFalse(host.getResponse().getContentAsString().contains(a.getRoomId()));

        JsonNode guestBody = body(join(a, patientToken));
        assertEquals("GUEST", guestBody.get("role").asText());

        verify(videoProvider, org.mockito.Mockito.times(2)).ensureRoom(VideoRoomIds.roomName(a.getRoomId()), 2);
        assertEquals(2, logRepository.findByAppointmentIdOrderByOccurredAtAsc(a.getId()).stream()
                .filter(l -> l.getEvent() == VideoSessionEventEnum.TOKEN_ISSUED).count());
    }

    @Test
    void terceiroEConsultaInexistenteRecebem404SemEmitirToken() throws Exception {
        Appointment a = inAppStartingIn(5);
        assertEquals(404, join(a, tokenFor(outsider)).getResponse().getStatus());
        mvc.perform(post("/appointments/" + UUID.randomUUID() + "/video/join").header("Authorization", bearer(psyToken)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/appointments/" + a.getId() + "/video/join")).andExpect(status().isUnauthorized());
        verify(videoProvider, never()).issueToken(anyString(), anyString(), anyInt());
    }

    @Test
    void consultaCanceladaPresencialOuComLinkExternoNaoAbreSala() throws Exception {
        Appointment cancelled = appointment(LocalDateTime.now().plusMinutes(5), 50, MeetingTypeEnum.VIDEO_CALL,
                MeetingProviderEnum.IN_APP, AppointmentStatusEnum.CANCELLED);
        Appointment noShow = appointment(LocalDateTime.now().plusMinutes(5), 50, MeetingTypeEnum.VIDEO_CALL,
                MeetingProviderEnum.IN_APP, AppointmentStatusEnum.NO_SHOW);
        Appointment inPerson = appointment(LocalDateTime.now().plusMinutes(5), 50, MeetingTypeEnum.IN_PERSON,
                null, AppointmentStatusEnum.ACCEPTED);
        Appointment external = appointment(LocalDateTime.now().plusMinutes(5), 50, MeetingTypeEnum.VIDEO_CALL,
                MeetingProviderEnum.EXTERNAL_LINK, AppointmentStatusEnum.ACCEPTED);

        assertCode(join(cancelled, psyToken), 409, "VIDEO_APPOINTMENT_INACTIVE");
        assertCode(join(noShow, patientToken), 409, "VIDEO_APPOINTMENT_INACTIVE");
        assertCode(join(inPerson, psyToken), 400, "VIDEO_NOT_IN_APP");
        assertCode(join(external, patientToken), 400, "VIDEO_NOT_IN_APP");
        verify(videoProvider, never()).issueToken(anyString(), anyString(), anyInt());
    }

    @Test
    void janelaDeEntradaAbreAntesEFechaDepoisComTolerancia() throws Exception {
        // Abre 10 min antes do início (padrão)
        assertCode(join(inAppStartingIn(30), patientToken), 409, "VIDEO_WINDOW_NOT_OPEN");
        assertEquals(200, join(inAppStartingIn(9), patientToken).getResponse().getStatus());

        // Em andamento
        assertEquals(200, join(inAppStartingIn(-20), psyToken).getResponse().getStatus());

        // Já CONCLUÍDA pelo job, mas dentro dos 15 min de tolerância após o fim
        Appointment justEnded = appointment(LocalDateTime.now().minusMinutes(55), 50, MeetingTypeEnum.VIDEO_CALL,
                MeetingProviderEnum.IN_APP, AppointmentStatusEnum.COMPLETED);
        assertEquals(200, join(justEnded, patientToken).getResponse().getStatus());

        // Fim há mais de 15 min
        Appointment old = appointment(LocalDateTime.now().minusMinutes(70), 50, MeetingTypeEnum.VIDEO_CALL,
                MeetingProviderEnum.IN_APP, AppointmentStatusEnum.COMPLETED);
        assertCode(join(old, psyToken), 409, "VIDEO_WINDOW_CLOSED");
    }

    @Test
    void mensagemDeJanelaFechadaInformaHorarioDeAbertura() throws Exception {
        Appointment a = inAppStartingIn(120);
        JsonNode error = body(join(a, patientToken));
        assertTrue(error.get("message").asText().startsWith("A sala abre em "));
        assertTrue(error.get("message").asText().contains(a.getStartDateTime().minusMinutes(10).toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES).toString()));
    }

    @Test
    void comFlagDesligadaEntradaRetorna503SemQuebrar() throws Exception {
        Appointment a = inAppStartingIn(5);
        videoProperties.setEnabled(false);
        try {
            assertCode(join(a, psyToken), 503, "VIDEO_UNAVAILABLE");
            // A agenda continua funcionando
            mvc.perform(get("/appointments").header("Authorization", bearer(psyToken))).andExpect(status().isOk());
        } finally {
            videoProperties.setEnabled(true);
        }
    }

    @Test
    void soPsicologoEncerraASalaParaTodos() throws Exception {
        Appointment a = inAppStartingIn(-5);
        mvc.perform(post("/appointments/" + a.getId() + "/video/end").header("Authorization", bearer(patientToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/appointments/" + a.getId() + "/video/end").header("Authorization", bearer(psyToken)))
                .andExpect(status().isNoContent());
        verify(videoProvider).closeRoom(VideoRoomIds.roomName(a.getRoomId()));
    }

    // ---------------------------------------------------------------- webhook

    private MvcResult webhook(String auth) throws Exception {
        var request = post("/webhooks/video").contentType("application/webhook+json").content("{\"event\":\"x\"}");
        if (auth != null) request.header("Authorization", auth);
        return mvc.perform(request).andReturn();
    }

    private void webhookEvent(String eventId, VideoWebhookEvent.Type type, Appointment a, User who) {
        when(videoProvider.parseWebhook(any(), any())).thenReturn(Optional.of(new VideoWebhookEvent(
                eventId, type, VideoRoomIds.roomName(a.getRoomId()), who == null ? null : who.getId().toString(), Instant.now())));
    }

    @Test
    void webhookComAssinaturaInvalidaRetorna401() throws Exception {
        when(videoProvider.parseWebhook(any(), any())).thenThrow(new InvalidWebhookSignatureException("x"));
        assertEquals(401, webhook("assinatura-falsa").getResponse().getStatus());
        assertEquals(401, webhook(null).getResponse().getStatus());
    }

    @Test
    void corpoGrandeERecusadoCom413AntesDeSerLido() throws Exception {
        // Webhook público: teto de 64 KB, recusado antes de verificar a assinatura.
        MvcResult webhook = mvc.perform(post("/webhooks/video").contentType("application/webhook+json")
                .header("Authorization", "assinado").content(new byte[65 * 1024])).andReturn();
        assertEquals(413, webhook.getResponse().getStatus());
        assertEquals("PAYLOAD_TOO_LARGE", body(webhook).get("code").asText());
        verify(videoProvider, never()).parseWebhook(any(), any());

        // Resto da API (sem arquivo): teto de 2 MB.
        MvcResult login = mvc.perform(post("/auth/login").contentType("application/json")
                .content(new byte[3 * 1024 * 1024])).andReturn();
        assertEquals(413, login.getResponse().getStatus());
    }

    @Test
    void webhookRegistraPresencaENotificaPacienteSoNaPrimeiraEntradaDoPsicologo() throws Exception {
        Appointment a = inAppStartingIn(-1);

        webhookEvent("EV_1", VideoWebhookEvent.Type.PARTICIPANT_JOINED, a, psychologist);
        assertEquals(200, webhook("assinado").getResponse().getStatus());
        // Reentrega do mesmo evento: ignorada
        assertEquals(200, webhook("assinado").getResponse().getStatus());
        webhookEvent("EV_2", VideoWebhookEvent.Type.PARTICIPANT_LEFT, a, psychologist);
        webhook("assinado");
        webhookEvent("EV_3", VideoWebhookEvent.Type.PARTICIPANT_JOINED, a, psychologist);
        webhook("assinado");
        webhookEvent("EV_4", VideoWebhookEvent.Type.ROOM_FINISHED, a, null);
        webhook("assinado");
        // Identidade que não pertence à consulta: não é registrada
        webhookEvent("EV_5", VideoWebhookEvent.Type.PARTICIPANT_JOINED, a, outsider);
        webhook("assinado");

        List<VideoSessionEventEnum> events = logRepository.findByAppointmentIdOrderByOccurredAtAsc(a.getId()).stream()
                .map(l -> l.getEvent()).toList();
        assertEquals(4, events.size());
        assertEquals(2, events.stream().filter(e -> e == VideoSessionEventEnum.PARTICIPANT_JOINED).count());
        assertTrue(events.contains(VideoSessionEventEnum.ROOM_FINISHED));

        assertEquals(1, notificationsOf(patient, NotificationType.VIDEO_PARTICIPANT_JOINED).size());
        // Webhook só registra: status da consulta intocado
        assertEquals(AppointmentStatusEnum.ACCEPTED, appointmentRepository.findById(a.getId()).orElseThrow().getStatus());
    }

    // ---------------------------------------------------------------- lembrete

    @Test
    void lembreteSaiUmaVezParaPsicologoEPacienteSemConteudoClinico() {
        Appointment soon = inAppStartingIn(10);
        Appointment later = inAppStartingIn(60);

        reminderService.sendDueReminders();
        reminderService.sendDueReminders();

        List<Notification> patientReminders = notificationsOf(patient, NotificationType.VIDEO_SESSION_REMINDER).stream()
                .filter(n -> soon.getId().equals(n.getReferenceId())).toList();
        assertEquals(1, patientReminders.size());
        assertFalse(patientReminders.get(0).getMessage().contains(psychologist.getFullName()));
        assertEquals(1, notificationsOf(psychologist, NotificationType.VIDEO_SESSION_REMINDER).stream()
                .filter(n -> soon.getId().equals(n.getReferenceId())).count());

        assertNotNull(appointmentRepository.findById(soon.getId()).orElseThrow().getVideoReminderSentAt());
        assertNull(appointmentRepository.findById(later.getId()).orElseThrow().getVideoReminderSentAt());
    }

    private List<Notification> notificationsOf(User user, NotificationType type) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(n -> n.getType() == type).toList();
    }

    private void assertCode(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertEquals(code, body(result).get("code").asText());
    }
}
