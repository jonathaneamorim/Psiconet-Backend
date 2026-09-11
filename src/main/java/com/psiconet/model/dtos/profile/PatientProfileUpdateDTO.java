package com.psiconet.model.dtos.profile;

import jakarta.validation.constraints.NotBlank;

public record PatientProfileUpdateDTO(
        @NotBlank(message = "O nome é obrigatório.")
        String fullName,

        String phone
) {}
