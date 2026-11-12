package com.psiconet.infra.web;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEC-08: tamanho de página com teto e ordenação só por campos permitidos no endpoint. */
class PaginationGuardIntegrationTest extends IntegrationTestBase {

    private String psyToken;

    @BeforeEach
    void setUp() throws Exception {
        User psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        psyToken = tokenFor(psychologist);
    }

    @Test
    void tamanhoDePaginaTemTeto() throws Exception {
        mvc.perform(get("/appointments").param("size", "100000").header(HttpHeaders.AUTHORIZATION, bearer(psyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void ordenacaoPorCampoForaDaListaERecusada() throws Exception {
        // Campo de relacionamento (antes: 500 ou "side channel" de ordenação por dado sensível)
        mvc.perform(get("/appointments").param("sort", "treatmentLink.patient.user.cpf,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(psyToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort").exists());

        // Endpoint sem ordenação configurável
        mvc.perform(get("/payments").param("sort", "amount").header(HttpHeaders.AUTHORIZATION, bearer(psyToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordenacaoPermitidaContinuaFuncionando() throws Exception {
        mvc.perform(get("/appointments").param("sort", "startDateTime,asc").header(HttpHeaders.AUTHORIZATION, bearer(psyToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/connections").param("sort", "connectedAt,desc").header(HttpHeaders.AUTHORIZATION, bearer(psyToken)))
                .andExpect(status().isOk());
    }

    @Test
    void ordenacaoDoAdminAceitaAsOpcoesDaTela() throws Exception {
        String adminToken = tokenFor(createUser(RoleEnum.ADMIN, UserStatusEnum.ACTIVE));
        mvc.perform(get("/admin/users").param("sort", "password").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/users").param("sort", "createdAt,desc").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }
}
