package com.psiconet.model.dtos.clinical.record;

import jakarta.validation.constraints.Size;

public record MedicalRecordUpdateDTO(
        @Size(max = 20000, message = "Texto muito longo (máx. 20.000 caracteres).") String demand,
        @Size(max = 20000, message = "Texto muito longo (máx. 20.000 caracteres).") String goals,
        @Size(max = 20000, message = "Texto muito longo (máx. 20.000 caracteres).") String history,
        @Size(max = 20000, message = "Texto muito longo (máx. 20.000 caracteres).") String procedures,
        @Size(max = 20000, message = "Texto muito longo (máx. 20.000 caracteres).") String closing,
        /** Versão lida (lock otimista): evita sobrescrever edição feita em outra aba/dispositivo. */
        Long version
) {}
