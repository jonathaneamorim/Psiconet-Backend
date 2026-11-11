package com.psiconet.model.dtos.clinical.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccessDenyDTO(
        @NotBlank(message = "A justificativa é obrigatória.")
        @Size(min = 10, max = 2000, message = "A justificativa deve ter entre 10 e 2000 caracteres.")
        String justification
) {}
