package com.psiconet.model.dtos.clinical.record;

import com.psiconet.model.enums.clinical.RecordAccessStatusEnum;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccessRequestDTO(
        UUID id,
        UUID treatmentLinkId,
        String patientName,
        String psychologistName,
        RecordAccessStatusEnum status,
        boolean activeGrant,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        LocalDateTime expiresAt,
        boolean exportAllowed,
        /** Justificativa da negativa (só para paciente titular e psicólogo; nunca vai por e-mail). */
        String justification
) {}
