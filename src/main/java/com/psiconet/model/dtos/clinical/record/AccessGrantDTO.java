package com.psiconet.model.dtos.clinical.record;

import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;

public record AccessGrantDTO(
        /** Opcional: acesso somente leitura até esta data/hora. */
        @Future(message = "A validade deve ser uma data futura.") LocalDateTime expiresAt,
        boolean exportAllowed
) {}
