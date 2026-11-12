package com.psiconet.controllers;

import com.psiconet.infra.mail.EmailMessage;
import com.psiconet.infra.mail.EmailRequestedEvent;
import com.psiconet.infra.mail.EmailTemplate;
import com.psiconet.model.entities.access.PasswordResetCode;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.access.PasswordResetCodeRepository;
import com.psiconet.services.interfaces.PasswordResetService;
import com.psiconet.support.IntegrationTestBase;
import com.psiconet.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.http.MediaType;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BUG-05: fluxo completo de redefinição de senha. */
@RecordApplicationEvents
class PasswordResetIntegrationTest extends IntegrationTestBase {

    private static final String NEW_PASSWORD = "Nova@Senha2";

    @Autowired private ApplicationEvents events;
    @Autowired private PasswordResetCodeRepository codeRepository;
    @Autowired private PasswordResetService passwordResetService;

    private String requestToken(User user) throws Exception {
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail()))))
                .andExpect(status().isOk());
        EmailMessage email = emailsTo(user, EmailTemplate.PASSWORD_RESET).get(0);
        String link = (String) email.variables().get("link");
        return URLDecoder.decode(link.substring(link.indexOf("token=") + 6), StandardCharsets.UTF_8);
    }

    private List<EmailMessage> emailsTo(User user, EmailTemplate template) {
        return events.stream(EmailRequestedEvent.class)
                .map(EmailRequestedEvent::message)
                .filter(m -> m.to().equals(user.getEmail()) && m.template() == template)
                .toList();
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "newPassword", password))));
    }

    @Test
    void fluxoCompletoTrocaASenhaDerrubaSessoesEAvisaPorEmail() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String oldSession = tokenFor(user);
        String token = requestToken(user);

        reset(token, NEW_PASSWORD).andExpect(status().isOk());

        login(user.getEmail(), NEW_PASSWORD);
        mvc.perform(get("/users/me").header("Authorization", bearer(oldSession))).andExpect(status().isUnauthorized());
        assertThat(emailsTo(user, EmailTemplate.PASSWORD_CHANGED)).hasSize(1);
    }

    @Test
    void tokenNaoPodeSerReutilizado() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = requestToken(user);
        reset(token, NEW_PASSWORD).andExpect(status().isOk());
        reset(token, "Outra@Senha3").andExpect(status().isBadRequest());
    }

    @Test
    void tokenExpiradoERecusado() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = requestToken(user);
        PasswordResetCode code = codeRepository.findAll().stream()
                .filter(c -> c.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
        code.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        codeRepository.save(code);

        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void redefinicaoSegueAPoliticaDeSenha() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = requestToken(user);
        reset(token, "fraca").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.newPassword").exists());
        reset(token, TestData.STRONG_PASSWORD + "x".repeat(70)).andExpect(status().isBadRequest());
    }

    @Test
    void contaNaoAtivaNaoRecebeLinkENaoRedefine() throws Exception {
        User blocked = createUser(RoleEnum.PATIENT, UserStatusEnum.BLOCKED);
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", blocked.getEmail()))))
                .andExpect(status().isOk());
        assertThat(emailsTo(blocked, EmailTemplate.PASSWORD_RESET)).isEmpty();

        // Token emitido enquanto ativa; conta bloqueada depois ⇒ não redefine
        User later = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = requestToken(later);
        User managed = userRepository.findById(later.getId()).orElseThrow();
        managed.setStatus(UserStatusEnum.INACTIVE);
        userRepository.save(managed);
        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void limpezaRemoveTokensUsadosEExpirados() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = requestToken(user);
        reset(token, NEW_PASSWORD).andExpect(status().isOk());

        User other = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        requestToken(other);
        PasswordResetCode expired = codeRepository.findAll().stream()
                .filter(c -> c.getUser().getId().equals(other.getId())).findFirst().orElseThrow();
        expired.setExpiresAt(LocalDateTime.now().minusDays(2));
        codeRepository.save(expired);

        passwordResetService.purgeStaleTokens();

        assertThat(codeRepository.findAll()).noneMatch(c ->
                c.getUser().getId().equals(user.getId()) || c.getUser().getId().equals(other.getId()));
    }
}
