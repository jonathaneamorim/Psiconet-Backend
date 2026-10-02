package com.psiconet.model.dtos.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketMessageCreateDTO(
        @NotBlank(message = "Escreva uma mensagem.")
        @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres.")
        String content
) {}
