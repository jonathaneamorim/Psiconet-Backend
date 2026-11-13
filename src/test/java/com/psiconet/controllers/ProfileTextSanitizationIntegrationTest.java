package com.psiconet.controllers;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nome/telefone exibidos a outros usuários são higienizados na escrita: caracteres de controle,
 * invisíveis e de sobrescrita bidirecional (U+202x) — que permitem falsear a exibição do nome —
 * são removidos. O escape na renderização segue sendo a defesa contra XSS.
 */
class ProfileTextSanitizationIntegrationTest extends IntegrationTestBase {

    @Test
    void nomeComCaracteresBidiEInvisiveisEHigienizadoNoUpdate() throws Exception {
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = tokenFor(patient);

        // "Ana" + sobrescrita RTL (U+202E) + zero-width space (U+200B) + tab de controle.
        String malicioso = "Ana‮​\tSilva";
        mvc.perform(patch("/patients/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", malicioso, "phone", "11​999999999"))))
                .andExpect(status().isNoContent());

        String stored = body(mvc.perform(get("/patients/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn()).get("fullName").asString();

        assertFalse(stored.contains("‮"), "sobrescrita bidirecional deve ser removida");
        assertFalse(stored.contains("​"), "caractere invisível deve ser removido");
        assertEquals("Ana Silva", stored);
    }
}
