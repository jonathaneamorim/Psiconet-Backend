package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticationDTO(
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Formato de e-mail inválido.")
        @Size(max = 254, message = "E-mail muito longo.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(max = 128, message = "Senha muito longa.")
        String password,

        // "Manter conectado": emite token de validade estendida (api.security.token.remember-me-hours)
        Boolean rememberMe
) {
    public boolean rememberMeEnabled() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
