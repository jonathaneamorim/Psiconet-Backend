package com.psiconet.model.dtos.profile;

import com.psiconet.model.entities.embeddable.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateDTO(
        @NotBlank(message = "O nome é obrigatório.")
        String fullName,

        String phone,

        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres.")
        String description,

        // Endereço fixo do consultório (opcional) — sugerido ao agendar consultas presenciais.
        Location officeAddress
) {}
