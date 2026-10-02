package com.psiconet.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psiconet.infra.exceptions.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respostas JSON (formato {@link ErrorResponse}) para falhas de autenticação/autorização que
 * acontecem na cadeia de filtros, antes de chegar aos controllers.
 */
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, ErrorResponse.withCode(
                HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
                "Sessão expirada ou inválida. Faça login novamente."));
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, ErrorResponse.withCode(
                HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "Você não tem permissão para acessar este recurso."));
    }

    public void write(HttpServletResponse response, ErrorResponse body) throws IOException {
        response.setStatus(body.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
