package com.psiconet.infra.exceptions;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    // Código estável para o frontend tomar decisões (ex.: PASSWORD_CHANGE_REQUIRED, RATE_LIMITED).
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, String> errors;
    // Consultas no mesmo horário (code SCHEDULE_OVERLAP, ADR 019) — só em respostas ao psicólogo dono.
    private List<?> conflicts;

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public static ErrorResponse withCode(int status, String code, String message) {
        ErrorResponse response = new ErrorResponse(status, message);
        response.setCode(code);
        return response;
    }

    public ErrorResponse(int status, String message, Map<String, String> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }
}