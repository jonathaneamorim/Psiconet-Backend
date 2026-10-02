package com.psiconet.model.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @NotBlank(message = "Informe a senha atual.")
        @Size(max = 128, message = "Senha muito longa.")
        String currentPassword,

        @NotBlank(message = "A nova senha é obrigatória.")
        @Size(max = 128, message = "Senha muito longa.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword
) {}
