package com.psiconet.model.dtos.clinical.record;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SessionNoteAddendumCreateDTO(
        @NotBlank(message = "Informe o motivo da retificação.")
        @Size(max = 500, message = "Motivo muito longo (máx. 500 caracteres).")
        String reason,
        @NotNull(message = "Conteúdo obrigatório.") JsonNode content
) {}
