package com.psiconet.model.dtos.clinical.record;

import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.ClinicalAuditActionEnum;

import java.time.LocalDateTime;

public record ClinicalAuditEntryDTO(
        long sequence,
        ClinicalAuditActionEnum action,
        RoleEnum actorRole,
        String actorName,
        LocalDateTime occurredAt
) {}
