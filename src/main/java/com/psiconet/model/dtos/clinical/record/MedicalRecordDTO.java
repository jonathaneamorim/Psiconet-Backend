package com.psiconet.model.dtos.clinical.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro documental do prontuário (campos estruturados inspirados na Res. CFP 001/2009:
 * demanda, objetivos, histórico, procedimentos adotados, encaminhamento/encerramento).
 */
public record MedicalRecordDTO(
        UUID recordId,
        UUID treatmentLinkId,
        String patientName,
        boolean linkActive,
        boolean opened,
        boolean canEdit,
        LocalDate openingDate,
        String demand,
        String goals,
        String history,
        String procedures,
        String closing,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {}
