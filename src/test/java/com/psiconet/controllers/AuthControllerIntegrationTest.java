package com.psiconet.controllers;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import com.psiconet.support.TestData;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends IntegrationTestBase {

    private Map<String, Object> patientPayload(String email, String cpf) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("fullName", "Maria da Silva");
        payload.put("cpf", cpf);
        payload.put("birthDate", "1995-05-10");
        payload.put("password", TestData.STRONG_PASSWORD);
        payload.put("acceptTerms", true);
        payload.put("acceptHealthData", true);
        payload.put("termsVersion", TestData.LEGAL_VERSION);
        payload.put("privacyVersion", TestData.LEGAL_VERSION);
        return payload;
    }

    // --- Cadastro (bug relatado: 1ª tentativa "sem ação", 2ª "conta já existe") ---

    @Test
    void cadastroNovoRetornaJsonComProximoPassoEPersisteUsuario() throws Exception {
        String email = TestData.uniqueEmail("novo");

        mvc.perform(post("/auth/register/patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientPayload(email, TestData.validCpf()))))
                .andExpect(status().isCreated())
                // Antes a resposta era texto puro e o apiClient do front quebrava no JSON.parse
                .andExpect(jsonPath("$.message").value(containsString("criada com sucesso")))
                .andExpect(jsonPath("$.nextStep").value("LOGIN"));

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(RoleEnum.PATIENT);
        assertThat(saved.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
        assertThat(patientRepository.findByUser(saved)).isPresent();

        // E-mail de boas-vindas enviado de forma assíncrona após o commit
        verify(mailSender, timeout(3000).atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void cadastroDuplicadoRetorna409ComErroNoCampoEmail() throws Exception {
        String email = TestData.uniqueEmail("dup");
        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientPayload(email, TestData.validCpf()))))
                .andExpect(status().isCreated());

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientPayload(email.toUpperCase(), TestData.validCpf()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value(containsString("já está cadastrado")))
                .andExpect(jsonPath("$.message").value(containsString("já está cadastrado")));
    }

    @Test
    void cpfDuplicadoNaoEcoaOCpfNaMensagem() throws Exception {
        String cpf = TestData.validCpf();
        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientPayload(TestData.uniqueEmail("cpf1"), cpf))))
                .andExpect(status().isCreated());

        String response = mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientPayload(TestData.uniqueEmail("cpf2"), cpf))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.cpf").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(cpf);
    }

    @Test
    void falhaNoEnvioDeEmailNaoDerrubaOCadastro() throws Exception {
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(MimeMessage.class));
        String email = TestData.uniqueEmail("smtpfail");

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON)
                        .content(json(patientPayload(email, TestData.validCpf()))))
                .andExpect(status().isCreated());

        verify(mailSender, timeout(3000).atLeastOnce()).send(any(MimeMessage.class));
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void cadastroPublicoIgnoraRoleAdminNoCorpo() throws Exception {
        String email = TestData.uniqueEmail("massassign");
        Map<String, Object> payload = patientPayload(email, TestData.validCpf());
        payload.put("role", "ADMIN");
        payload.put("status", "ACTIVE");
        payload.put("baseAdmin", true);

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(RoleEnum.PATIENT);
        assertThat(saved.isBaseAdmin()).isFalse();
    }

    @Test
    void validacaoRetornaErrosPorCampo() throws Exception {
        Map<String, Object> payload = patientPayload("invalido", "123");
        payload.put("password", "fraca");

        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.cpf").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // SEC-11: o BCrypt só considera os primeiros 72 bytes — senha maior passaria a valer pelo prefixo.
    @Test
    void senhaComMaisDe72BytesERecusadaNoCadastro() throws Exception {
        Map<String, Object> longa = patientPayload(TestData.uniqueEmail("longa"), TestData.validCpf());
        longa.put("password", TestData.STRONG_PASSWORD + "a".repeat(70));
        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(longa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(containsString("72")));

        // Acentos ocupam 2 bytes: 40 caracteres podem passar de 72 bytes
        Map<String, Object> acentuada = patientPayload(TestData.uniqueEmail("acento"), TestData.validCpf());
        acentuada.put("password", "Sé@" + "é".repeat(37));
        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(acentuada)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // BUG-02: senha nunca é "trimada" — com espaço nas pontas, cadastro e login usam a mesma senha.
    @Test
    void senhaComEspacosNasPontasFuncionaDoCadastroAoLogin() throws Exception {
        String email = TestData.uniqueEmail("espaco");
        Map<String, Object> payload = patientPayload(email, TestData.validCpf());
        payload.put("password", "  Senha@Forte1  ");
        mvc.perform(post("/auth/register/patient").contentType(MediaType.APPLICATION_JSON).content(json(payload)))
                .andExpect(status().isCreated());

        login(email, "  Senha@Forte1  ");
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Senha@Forte1"))))
                .andExpect(status().isUnauthorized());
    }

    // --- Login ---

    @Test
    void loginComSucessoRetornaTokenRoleEExpiracao() throws Exception {
        User user = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", TestData.STRONG_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("PSYCHOLOGIST"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void emailInexistenteESenhaErradaTemAMesmaResposta() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);

        String unknown = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", TestData.uniqueEmail("naoexiste"), "password", "Qualquer@123"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrongPassword = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", "Errada@123"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(unknown).get("message"))
                .isEqualTo(objectMapper.readTree(wrongPassword).get("message"));
    }

    @Test
    void contaInativaSoRevelaStatusComSenhaCorreta() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.INACTIVE);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", "Errada@123"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", TestData.STRONG_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void contaBloqueadaEPendenteTemMensagensProprias() throws Exception {
        User blocked = createUser(RoleEnum.PATIENT, UserStatusEnum.BLOCKED);
        User pending = createUser(RoleEnum.PATIENT, UserStatusEnum.PENDING_VERIFICATION);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", blocked.getEmail(), "password", TestData.STRONG_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", pending.getEmail(), "password", TestData.STRONG_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));
    }

    @Test
    void tokenDeixaDeValerQuandoUsuarioEhInativado() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = tokenFor(user);

        mvc.perform(get("/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        User managed = userRepository.findById(user.getId()).orElseThrow();
        managed.setStatus(UserStatusEnum.INACTIVE);
        userRepository.save(managed);

        mvc.perform(get("/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutInvalidaOToken() throws Exception {
        User user = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        String token = tokenFor(user);

        mvc.perform(post("/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void semTokenRetorna401Json() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }
}
