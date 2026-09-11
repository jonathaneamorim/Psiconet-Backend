package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record ValidateResetTokenRequestDTO(
        @NotBlank(message = "O token é obrigatório.")
        String token
) {}
