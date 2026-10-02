package com.psiconet.model.dtos.clinical.record;

import java.time.LocalDateTime;
import java.util.UUID;

/** Linha da lista de prontuários do psicólogo (um por vínculo). */
public record MedicalRecordSummaryDTO(
        UUID treatmentLinkId,
        UUID recordId,
        UUID patientUserId,
        String patientName,
        boolean linkActive,
        boolean recordOpened,
        long notesCount,
        long pendingAccessRequests,
        LocalDateTime updatedAt
) {}
