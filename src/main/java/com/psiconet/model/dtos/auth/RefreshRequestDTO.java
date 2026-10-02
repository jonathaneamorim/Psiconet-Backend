package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequestDTO(
        @NotBlank(message = "Sessão não informada.") @Size(max = 200) String refreshToken
) {}
