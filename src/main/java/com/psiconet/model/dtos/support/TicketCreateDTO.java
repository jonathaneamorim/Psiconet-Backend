package com.psiconet.model.dtos.support;

import com.psiconet.model.enums.support.TicketCategoryEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketCreateDTO(
        @NotNull(message = "Escolha uma categoria.")
        TicketCategoryEnum category,

        @NotBlank(message = "Informe o assunto.")
        @Size(min = 5, max = 120, message = "O assunto deve ter entre 5 e 120 caracteres.")
        String subject,

        @NotBlank(message = "Descreva o problema.")
        @Size(min = 10, max = 4000, message = "A descrição deve ter entre 10 e 4000 caracteres.")
        String description
) {}
