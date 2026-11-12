package com.psiconet.controllers;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import tools.jackson.databind.JsonNode;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.access.UserSession;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.access.SessionRevocationReasonEnum;
import com.psiconet.repositories.access.UserSessionRepository;
import com.psiconet.support.IntegrationTestBase;
import com.psiconet.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Manter login" e sessões por dispositivo com refresh rotativo (ADR 014). */
class SessionIntegrationTest extends IntegrationTestBase {

    @Autowired UserSessionRepository sessionRepository;

    private JsonNode loginBody(User user, boolean rememberMe) throws Exception {
        return body(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", TestData.STRONG_PASSWORD, "rememberMe", rememberMe))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private void expectAccess(String token, int status) throws Exception {
        mvc.perform(get("/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().is(status));
    }

    private UUID sessionIdOf(String refreshToken) {
        return UUID.fromString(refreshToken.substring(0, refreshToken.indexOf('.')));
    }

    private static Duration untilNow(JsonNode instant) {
        return Duration.between(Instant.now(), Instant.parse(instant.asString()));
    }

    @Test
    void semManterConectadoTokenCurtoESessaoDeAte2HorasSemUso() throws Exception {
        JsonNode login = loginBody(createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE), false);

        assertThat(login.get("rememberMe").asBoolean()).isFalse();
        assertThat(untilNow(login.get("expiresAt"))).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));
        assertThat(untilNow(login.get("refreshExpiresAt"))).isBetween(Duration.ofMinutes(119), Duration.ofHours(2));
        // O banco guarda só o hash do refresh
        UserSession session = sessionRepository.findById(sessionIdOf(login.get("refreshToken").asString())).orElseThrow();
        assertThat(login.get("refreshToken").asString()).doesNotContain(session.getCurrentTokenHash());
        assertThat(session.getAbsoluteExpiresAt()).isBefore(Instant.now().plus(12, ChronoUnit.HOURS).plusSeconds(5));
    }

    @Test
    void comManterConectadoSessaoDe7DiasComLimiteDe30() throws Exception {
        JsonNode login = loginBody(createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE), true);

        assertThat(login.get("rememberMe").asBoolean()).isTrue();
        assertThat(untilNow(login.get("expiresAt"))).isLessThanOrEqualTo(Duration.ofMinutes(15));
        assertThat(untilNow(login.get("refreshExpiresAt"))).isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
        UserSession session = sessionRepository.findById(sessionIdOf(login.get("refreshToken").asString())).orElseThrow();
        assertThat(Duration.between(session.getCreatedAt(), session.getAbsoluteExpiresAt())).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void refreshRotacionaEReutilizacaoDeTokenAntigoRevogaASessao() throws Exception {
        JsonNode login = loginBody(createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE), true);
        String r1 = login.get("refreshToken").asString();

        JsonNode second = body(refresh(r1).andExpect(status().isOk()).andReturn());
        String r2 = second.get("refreshToken").asString();
        assertThat(r2).isNotEqualTo(r1);
        assertThat(second.get("rememberMe").asBoolean()).isTrue();
        expectAccess(second.get("token").asString(), 200);

        // Requisição paralela com o refresh anterior (dentro da tolerância): só novo acesso, refresh mantido
        JsonNode parallel = body(refresh(r1).andExpect(status().isOk()).andReturn());
        assertThat(parallel.get("refreshToken").isNull()).isTrue();

        String r3 = body(refresh(r2).andExpect(status().isOk()).andReturn()).get("refreshToken").asString();

        // r1 agora tem duas gerações: reutilização ⇒ sessão inteira revogada
        refresh(r1).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        refresh(r3).andExpect(status().isUnauthorized());
        expectAccess(second.get("token").asString(), 401);
        assertThat(sessionRepository.findById(sessionIdOf(r1)).orElseThrow().getRevokedReason())
                .isEqualTo(SessionRevocationReasonEnum.REUSE_DETECTED);
    }

    @Test
    void refreshInvalidoOuForjadoE401() throws Exception {
        refresh("lixo").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        refresh(UUID.randomUUID() + ".segredo").andExpect(status().isUnauthorized());

        String real = loginBody(createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE), false).get("refreshToken").asString();
        refresh(real.substring(0, real.indexOf('.')) + ".adivinhado").andExpect(status().isUnauthorized());
        // Tentativa com segredo errado também derruba a sessão (o id vazou)
        refresh(real).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInativadoPerdeASessaoInclusiveORefresh() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        JsonNode login = loginBody(user, true);

        User managed = userRepository.findById(user.getId()).orElseThrow();
        managed.setStatus(UserStatusEnum.INACTIVE);
        userRepository.save(managed);

        expectAccess(login.get("token").asString(), 401);
        refresh(login.get("refreshToken").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    void inativacaoPeloAdminRevogaAsSessoes() throws Exception {
        User admin = createUser(RoleEnum.ADMIN, UserStatusEnum.ACTIVE);
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        JsonNode login = loginBody(patient, true);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/admin/users/" + patient.getId() + "/status")
                        .header("Authorization", bearer(tokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "INACTIVE", "reason", "Teste de revogação"))))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findById(sessionIdOf(login.get("refreshToken").asString())).orElseThrow().getRevokedReason())
                .isEqualTo(SessionRevocationReasonEnum.ADMIN);
    }

    @Test
    void logoutEncerraSoEsteDispositivoELogoutAllEncerraTodos() throws Exception {
        User user = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        JsonNode notebook = loginBody(user, true);
        JsonNode celular = loginBody(user, true);
        JsonNode tablet = loginBody(user, false);

        mvc.perform(post("/auth/logout").header("Authorization", bearer(notebook.get("token").asString())))
                .andExpect(status().isNoContent());
        expectAccess(notebook.get("token").asString(), 401);
        refresh(notebook.get("refreshToken").asString()).andExpect(status().isUnauthorized());
        expectAccess(celular.get("token").asString(), 200);

        mvc.perform(post("/auth/logout-all").header("Authorization", bearer(celular.get("token").asString())))
                .andExpect(status().isNoContent());
        expectAccess(celular.get("token").asString(), 401);
        expectAccess(tablet.get("token").asString(), 401);
        refresh(celular.get("refreshToken").asString()).andExpect(status().isUnauthorized());
        refresh(tablet.get("refreshToken").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    void trocaDeSenhaDerrubaOutrosDispositivosEMantemOAtual() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        JsonNode atual = loginBody(user, true);
        JsonNode outro = loginBody(user, false);

        JsonNode changed = body(mvc.perform(post("/auth/change-password").header("Authorization", bearer(atual.get("token").asString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", TestData.STRONG_PASSWORD, "newPassword", "OutraSenha@2026"))))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(changed.get("rememberMe").asBoolean()).isTrue();
        expectAccess(changed.get("token").asString(), 200);
        expectAccess(outro.get("token").asString(), 401);
        refresh(outro.get("refreshToken").asString()).andExpect(status().isUnauthorized());
        refresh(atual.get("refreshToken").asString()).andExpect(status().isUnauthorized());
        refresh(changed.get("refreshToken").asString()).andExpect(status().isOk());
    }

    @Test
    void sessaoExpiraPorInatividadeEPeloLimiteAbsoluto() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        JsonNode idle = loginBody(user, false);
        UserSession idleSession = sessionRepository.findById(sessionIdOf(idle.get("refreshToken").asString())).orElseThrow();
        idleSession.setIdleExpiresAt(Instant.now().minusSeconds(1));
        sessionRepository.save(idleSession);
        refresh(idle.get("refreshToken").asString()).andExpect(status().isUnauthorized());

        JsonNode absolute = loginBody(user, true);
        UserSession absSession = sessionRepository.findById(sessionIdOf(absolute.get("refreshToken").asString())).orElseThrow();
        // Campo não atualizável pelo Hibernate: ajusta direto no banco
        jdbc().update("UPDATE sessao_usuario SET expira_absoluta_em = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), absSession.getId());
        expectAccess(absolute.get("token").asString(), 401);
        refresh(absolute.get("refreshToken").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSemSessaoNoFormatoAntigoERecusado() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String legacy = JWT.create().withIssuer("psiconet").withSubject(user.getEmail())
                .withClaim("role", "PATIENT").withClaim("ver", 0)
                .withExpiresAt(Instant.now().plusSeconds(600))
                .sign(Algorithm.HMAC256("segredo-de-teste-com-mais-de-32-caracteres-0123456789"));
        expectAccess(legacy, 401);
    }

    @Autowired javax.sql.DataSource dataSource;

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }
}
