package com.psiconet.controllers.support;

import tools.jackson.databind.JsonNode;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "psiconet.tickets.max-active-per-user=2",
        "psiconet.tickets.min-interval-between-openings=0s"
})
class TicketFlowIntegrationTest extends IntegrationTestBase {

    private JsonNode open(String token, String subject) throws Exception {
        return body(mvc.perform(post("/tickets").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("category", "ACCOUNT", "subject", subject,
                                "description", "Descrição detalhada do problema " + subject))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    @Test
    void fluxoCompletoAberturaRespostaEFinalizacao() throws Exception {
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String patientToken = tokenFor(patient);
        String adminToken = tokenFor(createUser(RoleEnum.ADMIN, UserStatusEnum.ACTIVE));

        JsonNode ticket = open(patientToken, "Erro ao salvar perfil");
        String id = ticket.get("id").asString();

        mvc.perform(get("/tickets").header("Authorization", bearer(patientToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));

        // Admin responde → IN_PROGRESS + notificação + e-mail
        mvc.perform(post("/admin/tickets/" + id + "/messages").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "Estamos verificando."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        verify(mailSender, timeout(3000).atLeastOnce()).send(any(MimeMessage.class));

        // Usuário vê a resposta como "Equipe Psiconet"
        mvc.perform(get("/tickets/" + id).header("Authorization", bearer(patientToken)))
                .andExpect(jsonPath("$.messages[0].authorName").value("Equipe Psiconet"))
                .andExpect(jsonPath("$.messages[0].fromSupport").value(true));

        mvc.perform(get("/notifications").header("Authorization", bearer(patientToken)))
                .andExpect(jsonPath("$[0].type").value("TICKET_REPLIED"))
                .andExpect(jsonPath("$[0].referenceId").value(id));

        // Finalização → TICKET_CLOSED
        mvc.perform(patch("/admin/tickets/" + id + "/status").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "RESOLVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/notifications").header("Authorization", bearer(patientToken)))
                .andExpect(jsonPath("$[0].type").value("TICKET_CLOSED"));
    }

    @Test
    void usuarioNaoAcessaChamadoDeOutro() throws Exception {
        String ownerToken = tokenFor(createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE));
        String otherToken = tokenFor(createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE));
        String id = open(ownerToken, "Chamado privado").get("id").asString();

        mvc.perform(get("/tickets/" + id).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/tickets/" + id + "/messages").header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "intrusão"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void limiteDeChamadosAbertosEDuplicidade() throws Exception {
        String token = tokenFor(createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE));
        open(token, "Primeiro chamado");

        // mesmo conteúdo → 409
        mvc.perform(post("/tickets").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("category", "ACCOUNT", "subject", "Primeiro chamado",
                                "description", "Descrição detalhada do problema Primeiro chamado"))))
                .andExpect(status().isConflict());

        open(token, "Segundo chamado");

        // limite de 2 ativos (configurado no teste)
        mvc.perform(post("/tickets").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("category", "OTHER", "subject", "Terceiro chamado",
                                "description", "Mais um problema para relatar aqui"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ticket").exists());
    }

    @Test
    void mensagemDuplicadaETamanhoMaximo() throws Exception {
        String token = tokenFor(createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE));
        String id = open(token, "Chamado de mensagens").get("id").asString();

        mvc.perform(post("/tickets/" + id + "/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "Alguma novidade?"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/tickets/" + id + "/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "  alguma   NOVIDADE? "))))
                .andExpect(status().isConflict());
        mvc.perform(post("/tickets/" + id + "/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "x".repeat(4001)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminNaoAbreChamadoPelaRotaDeUsuario() throws Exception {
        String adminToken = tokenFor(createUser(RoleEnum.ADMIN, UserStatusEnum.ACTIVE));
        mvc.perform(post("/tickets").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("category", "OTHER", "subject", "Teste admin", "description", "Descrição longa o bastante"))))
                .andExpect(status().isForbidden());
    }
}
