package com.psiconet.controllers.clinical;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BUG-07b: limites de sanidade do agendamento (duração e antecedência máxima). */
class AppointmentLimitsIntegrationTest extends IntegrationTestBase {

    @Autowired TreatmentLinkRepository linkRepository;

    private User patient;
    private String psyToken;

    @BeforeEach
    void setUp() throws Exception {
        User psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        TreatmentLink link = new TreatmentLink();
        link.setPsychologist(psychologistRepository.findByUserId(psychologist.getId()).orElseThrow());
        link.setPatient(patientRepository.findByUserId(patient.getId()).orElseThrow());
        link.setIsActive(true);
        link.setStartDate(LocalDate.now());
        link.setDefaultPrice(new BigDecimal("150.00"));
        linkRepository.save(link);
        psyToken = tokenFor(psychologist);
    }

    private Map<String, Object> body(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patient.getId());
        body.put("startDateTime", start.truncatedTo(ChronoUnit.MINUTES).toString());
        if (end != null) body.put("endDateTime", end.truncatedTo(ChronoUnit.MINUTES).toString());
        body.put("meetingType", "VIDEO_CALL");
        body.put("meetingLink", "https://meet.example.com/x");
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions create(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/appointments").header("Authorization", bearer(psyToken))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void duracaoMuitoCurtaOuLongaERecusada() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0);
        create(body(start, start.plusMinutes(5))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.endDateTime").exists());
        create(body(start, start.plusHours(9))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.endDateTime").exists());
    }

    @Test
    void dataAlemDeUmAnoERecusada() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusYears(1).plusDays(2);
        create(body(start, null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.startDateTime").exists());
    }

    @Test
    void consultaNormalContinuaAceita() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(5).withHour(15).withMinute(0);
        create(body(start, start.plusMinutes(50))).andExpect(status().isOk());
    }
}
