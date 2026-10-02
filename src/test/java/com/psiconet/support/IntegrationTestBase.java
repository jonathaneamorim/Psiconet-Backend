package com.psiconet.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psiconet.infra.ratelimit.RateLimiterService;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integração: contexto completo + MockMvc + H2. Sem @Transactional nos testes
 * de propósito — os e-mails são disparados AFTER_COMMIT e precisamos que o commit aconteça.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PatientRepository patientRepository;
    @Autowired protected PsychologistRepository psychologistRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected RateLimiterService rateLimiterService;

    @MockBean protected JavaMailSender mailSender;

    @BeforeEach
    void baseSetUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        rateLimiterService.reset();
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected User createUser(RoleEnum role, UserStatusEnum status) {
        User user = new User();
        user.setEmail(TestData.uniqueEmail(role.name().toLowerCase()));
        user.setCpf(TestData.validCpf());
        user.setFullName("Usuário " + role.name());
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setPassword(passwordEncoder.encode(TestData.STRONG_PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        // Usuários de teste já aceitaram os documentos vigentes (o reaceite tem testes próprios).
        user.setAcceptedTermsVersion(TestData.LEGAL_VERSION);
        user.setAcceptedPrivacyVersion(TestData.LEGAL_VERSION);
        user = userRepository.save(user);

        if (role == RoleEnum.PATIENT) {
            Patient patient = new Patient();
            patient.setUser(user);
            patientRepository.save(patient);
        } else if (role == RoleEnum.PSYCHOLOGIST) {
            Psychologist psychologist = new Psychologist();
            psychologist.setUser(user);
            psychologist.setCrp(TestData.uniqueCrp());
            psychologistRepository.save(psychologist);
        }
        return user;
    }

    protected String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).get("token").asText();
    }

    protected String tokenFor(User user) throws Exception {
        return login(user.getEmail(), TestData.STRONG_PASSWORD);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
