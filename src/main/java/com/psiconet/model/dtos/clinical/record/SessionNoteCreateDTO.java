package com.psiconet.model.dtos.clinical.record;

import com.fasterxml.jackson.databind.JsonNode;
import com.psiconet.model.enums.clinical.SessionNoteTypeEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SessionNoteCreateDTO(
        /** Consulta à qual a anotação se refere (deve pertencer ao vínculo). Opcional. */
        UUID appointmentId,
        @NotNull(message = "Informe o tipo da anotação.") SessionNoteTypeEnum type,
        @Size(max = 150, message = "Título muito longo.") String title,
        /** Documento do editor (JSON). Validado por whitelist no backend. */
        @NotNull(message = "Conteúdo obrigatório.") JsonNode content
) {}
