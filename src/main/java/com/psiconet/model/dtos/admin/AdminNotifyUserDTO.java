package com.psiconet.model.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Comunicado do admin para um usuário: notificação no site + e-mail (template "comunicado"). */
public record AdminNotifyUserDTO(
        @NotNull(message = "Informe o destinatário.")
        UUID recipientId,

        @NotBlank(message = "Informe o título.")
        @Size(min = 3, max = 120, message = "O título deve ter entre 3 e 120 caracteres.")
        String title,

        @NotBlank(message = "Escreva a mensagem.")
        @Size(min = 3, max = 2000, message = "A mensagem deve ter entre 3 e 2000 caracteres.")
        String message
) {}
