package com.psiconet.model.dtos.clinical.record;

import com.fasterxml.jackson.databind.JsonNode;
import com.psiconet.model.enums.clinical.SessionNoteTypeEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SessionNoteDraftUpdateDTO(
        @NotNull(message = "Informe o tipo da anotação.") SessionNoteTypeEnum type,
        @Size(max = 150, message = "Título muito longo.") String title,
        @NotNull(message = "Conteúdo obrigatório.") JsonNode content,
        /** Versão do rascunho lida pelo editor (detecta edição concorrente em outra aba). */
        Long version
) {}
