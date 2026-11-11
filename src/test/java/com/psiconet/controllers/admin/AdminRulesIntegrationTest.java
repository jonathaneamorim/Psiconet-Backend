package com.psiconet.controllers.admin;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.access.ChangeLogRepository;
import com.psiconet.support.IntegrationTestBase;
import com.psiconet.support.TestData;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRulesIntegrationTest extends IntegrationTestBase {

    @Autowired ChangeLogRepository changeLogRepository;

    private User admin() {
        return createUser(RoleEnum.ADMIN, UserStatusEnum.ACTIVE);
    }

    private void patchStatus(String token, User target, String status, int expected) throws Exception {
        mvc.perform(patch("/admin/users/" + target.getId() + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", status))))
                .andExpect(status().is(expected));
    }

    @Test
    void adminBaseCriadoNaInicializacaoExigeTrocaDeSenha() throws Exception {
        User base = userRepository.findFirstByBaseAdminTrue().orElseThrow();
        assertThat(base.getRole()).isEqualTo(RoleEnum.ADMIN);

        // Só testa o fluxo de 1º acesso se a senha inicial ainda não foi trocada por outro teste
        if (!base.isMustChangePassword()) return;

        String token = login(base.getEmail(), "Admin@Teste123");
        mvc.perform(get("/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        String newToken = body(mvc.perform(post("/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Admin@Teste123", "newPassword", "NovaSenha@2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn()).get("token").asText();

        // token antigo morreu; o novo ainda exige o 1º aceite dos Termos/Privacidade (ADR 013)
        mvc.perform(get("/admin/users").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/users").header("Authorization", bearer(newToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        mvc.perform(post("/legal/consents").header("Authorization", bearer(newToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("acceptTerms", true, "termsVersion", com.psiconet.support.TestData.LEGAL_VERSION, "privacyVersion", com.psiconet.support.TestData.LEGAL_VERSION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(false));
        mvc.perform(get("/admin/users").header("Authorization", bearer(newToken))).andExpect(status().isOk());
        mvc.perform(get("/legal/consents/me").header("Authorization", bearer(newToken)))
                .andExpect(jsonPath("$[0].source").value("ADMIN_FIRST_ACCESS"));
    }

    @Test
    void adminNaoPodeInativarASiMesmo() throws Exception {
        User admin = admin();
        patchStatus(tokenFor(admin), admin, "INACTIVE", 400);
    }

    @Test
    void adminBaseNaoPodeSerInativado() throws Exception {
        User base = userRepository.findFirstByBaseAdminTrue().orElseThrow();
        patchStatus(tokenFor(admin()), base, "INACTIVE", 400);
        assertThat(userRepository.findById(base.getId()).orElseThrow().getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
    }

    @Test
    void inativarUsuarioDerrubaSessaoEAudita() throws Exception {
        String adminToken = tokenFor(admin());
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String patientToken = tokenFor(patient);
        long logsBefore = changeLogRepository.count();

        patchStatus(adminToken, patient, "INACTIVE", 204);

        mvc.perform(get("/notifications").header("Authorization", bearer(patientToken)))
                .andExpect(status().isUnauthorized());
        assertThat(changeLogRepository.count()).isGreaterThan(logsBefore);

        // Reativar não ressuscita o token antigo (versão incrementada)
        patchStatus(adminToken, patient, "ACTIVE", 204);
        mvc.perform(get("/notifications").header("Authorization", bearer(patientToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listaUsuariosComCpfMascaradoEFiltros() throws Exception {
        String adminToken = tokenFor(admin());
        User psy = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        String cpf = psy.getCpf();

        String response = mvc.perform(get("/admin/users")
                        .param("search", psy.getEmail())
                        .param("role", "PSYCHOLOGIST")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].maskedCpf")
                        .value("•••." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-••"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(cpf);
    }

    @Test
    void criarAdminGeraContaNovaComTrocaObrigatoriaEConvite() throws Exception {
        User creator = admin();
        String email = TestData.uniqueEmail("novoadmin");

        mvc.perform(post("/admin/admins").header("Authorization", bearer(tokenFor(creator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Novo Admin", "email", email,
                                "cpf", TestData.validCpf(), "temporaryPassword", "Temp@Senha1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.isBaseAdmin()).isFalse();
        assertThat(created.getCreatedBy().getId()).isEqualTo(creator.getId());
        verify(mailSender, timeout(3000).atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void pacienteOuPsicologoNaoViramAdmin() throws Exception {
        String adminToken = tokenFor(admin());
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);

        // Criar admin com e-mail de conta existente é recusado
        mvc.perform(post("/admin/admins").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Tentativa", "email", patient.getEmail(),
                                "cpf", TestData.validCpf(), "temporaryPassword", "Temp@Senha1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").exists());

        // Edição administrativa ignora "role"
        mvc.perform(put("/admin/users/" + patient.getId()).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Paciente Editado", "role", "ADMIN"))))
                .andExpect(status().isNoContent());

        User reloaded = userRepository.findById(patient.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(RoleEnum.PATIENT);
        assertThat(reloaded.getFullName()).isEqualTo("Paciente Editado");
    }

    @Test
    void dashboardRetornaIndicadoresAgregados() throws Exception {
        mvc.perform(get("/admin/dashboard").header("Authorization", bearer(tokenFor(admin()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThan(0)))
                .andExpect(jsonPath("$.activePsychologists").isNumber())
                .andExpect(jsonPath("$.activePatients").isNumber())
                .andExpect(jsonPath("$.tickets.open").isNumber())
                .andExpect(jsonPath("$.tickets.pending").isNumber())
                .andExpect(jsonPath("$.tickets.finished").isNumber());
    }

    @Test
    void comunicadoGeraNotificacaoEEmail() throws Exception {
        String adminToken = tokenFor(admin());
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);

        mvc.perform(post("/admin/notifications").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("recipientId", patient.getId(), "title", "Manutenção",
                                "message", "<b>Sábado</b> teremos manutenção."))))
                .andExpect(status().isCreated());

        mvc.perform(get("/notifications").header("Authorization", bearer(tokenFor(patient))))
                .andExpect(jsonPath("$[0].type").value("ADMIN_ANNOUNCEMENT"))
                .andExpect(jsonPath("$[0].title").value("Manutenção"));
        verify(mailSender, timeout(3000).atLeastOnce()).send(any(MimeMessage.class));
    }
}
