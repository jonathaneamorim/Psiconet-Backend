package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(
        @NotBlank(message = "O token é obrigatório.")
        String token,

        @NotBlank(message = "A nova senha é obrigatória.")
        @Size(max = 128, message = "Senha muito longa.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword
) {}
