package com.psiconet.infra.exceptions;

import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** SEC-09: erro do cliente nunca vira 500, sempre em JSON pt-BR e sem detalhe interno. */
class ErrorHandlingIntegrationTest extends IntegrationTestBase {

    private String psyToken;

    @BeforeEach
    void setUp() throws Exception {
        psyToken = tokenFor(createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE));
    }

    @Test
    void jsonMalformadoRetorna400() throws Exception {
        assertError(400, post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":"));
    }

    @Test
    void contentTypeNaoSuportadoRetorna415() throws Exception {
        assertError(415, post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"));
    }

    @Test
    void uuidInvalidoNoCaminhoRetorna400() throws Exception {
        assertError(400, authed(get("/appointments/nao-e-uuid")));
    }

    @Test
    void enumInvalidoNoParametroRetorna400() throws Exception {
        assertError(400, authed(get("/payments").param("status", "INEXISTENTE")));
    }

    @Test
    void metodoNaoPermitidoRetorna405() throws Exception {
        assertError(405, authed(delete("/notifications/unread-count")));
    }

    @Test
    void rotaInexistenteRetorna404() throws Exception {
        assertError(404, authed(get("/rota/que/nao/existe")));
    }

    @Test
    void arquivoAusenteNoMultipartRetorna400() throws Exception {
        assertError(400, authed(multipart("/psychologists/me/photo")));
    }

    @Test
    void parametroObrigatorioAusenteRetorna400() throws Exception {
        assertError(400, authed(get("/appointments/stats")));
    }

    // BUG-07g: mês/ano fora do intervalo não pode estourar como 500
    @Test
    void mesInvalidoNasEstatisticasRetorna400() throws Exception {
        assertError(400, authed(get("/appointments/stats").param("year", "2026").param("month", "13")));
        assertError(400, authed(get("/appointments/stats").param("year", "-5").param("month", "1")));
        assertError(400, authed(get("/payments/summary").param("year", "2026").param("month", "0")));
    }

    @Test
    void mensagensDeValidacaoEmPortugues() throws Exception {
        MvcResult result = mvc.perform(authed(post("/appointments")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
        String bodyText = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(bodyText.contains("must not"), bodyText);
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(psyToken));
    }

    private void assertError(int status, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String bodyText = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(status, result.getResponse().getStatus(), bodyText);
        assertTrue(String.valueOf(result.getResponse().getContentType()).startsWith("application/json"), bodyText);
        assertEquals(status, body(result).get("status").asInt());
        assertFalse(bodyText.contains("Exception"), "não vaza nome de classe: " + bodyText);
        assertFalse(bodyText.contains("at com."), "não vaza stack trace: " + bodyText);
    }
}
