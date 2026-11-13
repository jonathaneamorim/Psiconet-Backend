package com.psiconet.controllers.legal;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.legal.UserConsent;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.legal.ConsentSourceEnum;
import com.psiconet.model.enums.legal.ConsentTypeEnum;
import com.psiconet.repositories.legal.UserConsentRepository;
import com.psiconet.support.IntegrationTestBase;
import com.psiconet.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegalConsentIntegrationTest extends IntegrationTestBase {

    private static final String CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    @Autowired UserConsentRepository consentRepository;

    private Map<String, Object> patientPayload(String email) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("fullName", "Paciente Consentimento");
        payload.put("cpf", TestData.validCpf());
        payload.put("birthDate", "1995-05-10");
        payload.put("password", TestData.STRONG_PASSWORD);
        payload.put("acceptTerms", true);
        payload.put("acceptHealthData", true);
        payload.put("termsVersion", TestData.LEGAL_VERSION);
        payload.put("privacyVersion", TestData.LEGAL_VERSION);
        return payload;
    }

    private Map<String, Object> psychologistPayload(String email) {
        Map<String, Object> payload = patientPayload(email);
        payload.remove("acceptHealthData");
        payload.put("crp", TestData.uniqueCrp());
        return payload;
    }

    // --- Documentos públicos ---

    @Test
    void versoesEDocumentosSaoPublicos() throws Exception {
        mvc.perform(get("/legal/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms.version").value(TestData.LEGAL_VERSION))
                .andExpect(jsonPath("$.privacy.version").value(TestData.LEGAL_VERSION))
                .andExpect(jsonPath("$.cookies.version").exists());

        mvc.perform(get("/legal/documents/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Termos de Uso"))
                .andExpect(jsonPath("$.content").value(containsString("CVV")))
                // O cabeçalho de metadados não vaza para o conteúdo
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("versao:"))));

        mvc.perform(get("/legal/documents/privacy")).andExpect(status().isOk());
        mvc.perform(get("/legal/documents/cookies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(containsString("psiconet_refresh")));
        mvc.perform(get("/legal/documents/inexistente")).andExpect(status().isNotFound());
    }

    // --- Aceite no cadastro ---

    @Test
    void cadastroSemAceiteERecusadoENadaEPersistido() throws Exception {
        String email = TestData.uniqueEmail("semaceite");
        Map<String, Object> payload = patientPayload(email);
        payload.put("acceptTerms", false);

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.acceptTerms").exists());
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void pacienteSemConsentimentoDeDadosDeSaudeERecusado() throws Exception {
        String email = TestData.uniqueEmail("semsaude");
        Map<String, Object> payload = patientPayload(email);
        payload.remove("acceptHealthData");

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.acceptHealthData").exists());
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void cadastroComVersaoDesatualizadaERecusadoComCodigoProprio() throws Exception {
        String email = TestData.uniqueEmail("versaovelha");
        Map<String, Object> payload = patientPayload(email);
        payload.put("termsVersion", "0.9.0");

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEGAL_VERSION_OUTDATED"));
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void menorDeIdadeNaoSeCadastra() throws Exception {
        String email = TestData.uniqueEmail("menor");
        Map<String, Object> payload = patientPayload(email);
        payload.put("birthDate", LocalDate.now().minusYears(18).plusDays(1).toString());

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.birthDate").value(containsString("18 anos")));
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void cadastroDePacienteRegistraProvaImutavelDoAceiteComDadosDoServidor() throws Exception {
        String email = TestData.uniqueEmail("comaceite");
        Map<String, Object> payload = patientPayload(email);
        // Tentativa de forjar a data do aceite: deve ser ignorada
        payload.put("acceptedAt", "2000-01-01T00:00:00Z");
        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.7")
                        .header("X-Client-User-Agent", CHROME_UA)
                        .content(json(payload)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getAcceptedTermsVersion()).isEqualTo(TestData.LEGAL_VERSION);
        assertThat(user.getAcceptedPrivacyVersion()).isEqualTo(TestData.LEGAL_VERSION);

        List<UserConsent> consents = consentRepository.findByUserOrderByAcceptedAtDesc(user);
        assertThat(consents).extracting(UserConsent::getType)
                .containsExactlyInAnyOrder(ConsentTypeEnum.TERMS, ConsentTypeEnum.PRIVACY, ConsentTypeEnum.HEALTH_DATA);
        assertThat(consents).allSatisfy(c -> {
            assertThat(c.getSource()).isEqualTo(ConsentSourceEnum.REGISTRATION);
            assertThat(c.getVersion()).isEqualTo(TestData.LEGAL_VERSION);
            assertThat(c.getAcceptedAt()).isAfter(before);
            assertThat(c.getIpAddress()).isEqualTo("203.0.113.7");
            assertThat(c.getUserAgent()).isEqualTo("Chrome 141 · Windows");
        });

        String token = login(email, TestData.STRONG_PASSWORD);
        mvc.perform(get("/users/me").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/legal/consents/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // O histórico do titular não expõe IP nem navegador
                .andExpect(jsonPath("$[0].ipAddress").doesNotExist());
    }

    @Test
    void cadastroDePsicologoRegistraTermosEPrivacidade() throws Exception {
        String email = TestData.uniqueEmail("psiaceite");
        mvc.perform(post("/auth/register/psychologist").contentType(MediaType.APPLICATION_JSON)
                        .content(json(psychologistPayload(email))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(consentRepository.findByUserOrderByAcceptedAtDesc(user)).extracting(UserConsent::getType)
                .containsExactlyInAnyOrder(ConsentTypeEnum.TERMS, ConsentTypeEnum.PRIVACY);
    }

    // --- Reaceite ---

    @Test
    void usuarioComVersaoAntigaFicaBloqueadoAteReaceitar() throws Exception {
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        patient.setAcceptedTermsVersion("0.9.0");
        patient.setAcceptedPrivacyVersion(null);
        userRepository.save(patient);

        String token = body(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", patient.getEmail(), "password", TestData.STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andReturn()).get("token").asString();

        // Resto da API bloqueado; status, documentos e logout liberados
        mvc.perform(get("/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(get("/legal/consents/status").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending", hasItems("TERMS", "PRIVACY", "HEALTH_DATA")));
        mvc.perform(get("/legal/documents/privacy").header("Authorization", bearer(token))).andExpect(status().isOk());

        // Paciente precisa do consentimento de dados de saúde
        mvc.perform(post("/legal/consents").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("acceptTerms", true, "termsVersion", TestData.LEGAL_VERSION, "privacyVersion", TestData.LEGAL_VERSION))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.acceptHealthData").exists());

        // Versão que mudou enquanto a tela estava aberta
        mvc.perform(post("/legal/consents").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("acceptTerms", true, "acceptHealthData", true, "termsVersion", "0.9.0", "privacyVersion", TestData.LEGAL_VERSION))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEGAL_VERSION_OUTDATED"));

        mvc.perform(post("/legal/consents").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("acceptTerms", true, "acceptHealthData", true, "termsVersion", TestData.LEGAL_VERSION, "privacyVersion", TestData.LEGAL_VERSION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(false));

        mvc.perform(get("/notifications").header("Authorization", bearer(token))).andExpect(status().isOk());
        assertThat(consentRepository.findByUserOrderByAcceptedAtDesc(userRepository.findById(patient.getId()).orElseThrow()))
                .extracting(UserConsent::getSource).containsOnly(ConsentSourceEnum.REACCEPTANCE);
    }

    @Test
    void historicoDeConsentimentoExigeLogin() throws Exception {
        mvc.perform(get("/legal/consents/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/legal/consents").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void respostasDaApiTemCabecalhosDeSeguranca() throws Exception {
        mvc.perform(get("/legal/versions"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void versoesPodemSerCacheadas() throws Exception {
        mvc.perform(get("/legal/versions")).andExpect(header().string("Cache-Control", containsString("max-age")));
    }
}
