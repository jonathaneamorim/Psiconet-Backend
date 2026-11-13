package com.psiconet.controllers;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.conection.ConectionStatusEnum;
import com.psiconet.repositories.profile.ConnectionRepository;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Solicitações de conexão enviadas (GET /connections/sent) × recebidas (GET /connections/pending). */
class ConnectionRequestsIntegrationTest extends IntegrationTestBase {

    @Autowired ConnectionRepository connectionRepository;

    @Test
    void remetenteVeEnviadasEDestinatarioVeRecebidas() throws Exception {
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        User psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        User outsider = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        String patientToken = tokenFor(patient);
        String psyToken = tokenFor(psychologist);
        String outsiderToken = tokenFor(outsider);

        mvc.perform(post("/connections/" + psychologist.getId()).header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isOk());

        JsonNode sent = list("/connections/sent", patientToken);
        assertEquals(1, sent.get("content").size());
        JsonNode request = sent.get("content").get(0);
        assertEquals(psychologist.getId().toString(), request.get("receiver").get("id").asString());
        assertEquals("PENDING", request.get("status").asString());

        // Enviadas não aparecem como recebidas para quem enviou, e vice-versa
        assertEquals(0, list("/connections/pending", patientToken).get("content").size());
        assertEquals(0, list("/connections/sent", psyToken).get("content").size());
        assertEquals(1, list("/connections/pending", psyToken).get("content").size());
        // Terceiro não vê nada
        assertEquals(0, list("/connections/sent", outsiderToken).get("content").size());
        assertEquals(0, list("/connections/pending", outsiderToken).get("content").size());
    }

    @Test
    void remetenteCancelaSolicitacaoEnviada() throws Exception {
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        User psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        String patientToken = tokenFor(patient);
        String psyToken = tokenFor(psychologist);

        mvc.perform(post("/connections/" + psychologist.getId()).header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isOk());
        String id = list("/connections/sent", patientToken).get("content").get(0).get("id").asString();

        mvc.perform(delete("/connections/" + id).header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isNoContent());

        assertEquals(ConectionStatusEnum.REMOVED, connectionRepository.findById(UUID.fromString(id)).orElseThrow().getStatus());
        assertEquals(0, list("/connections/sent", patientToken).get("content").size());
        assertEquals(0, list("/connections/pending", psyToken).get("content").size());
    }

    @Test
    void aceitarTiraDasPendentesEPoeNasAtivas() throws Exception {
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        User psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        String patientToken = tokenFor(patient);
        String psyToken = tokenFor(psychologist);

        mvc.perform(post("/connections/" + patient.getId()).header(HttpHeaders.AUTHORIZATION, bearer(psyToken)))
                .andExpect(status().isOk());
        String id = list("/connections/pending", patientToken).get("content").get(0).get("id").asString();

        mvc.perform(patch("/connections/" + id + "/accept").header(HttpHeaders.AUTHORIZATION, bearer(patientToken)))
                .andExpect(status().isNoContent());

        assertEquals(0, list("/connections/sent", psyToken).get("content").size());
        assertEquals(0, list("/connections/pending", patientToken).get("content").size());
        assertEquals(1, list("/connections", patientToken).get("content").size());
    }

    private JsonNode list(String path, String token) throws Exception {
        return body(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }
}
