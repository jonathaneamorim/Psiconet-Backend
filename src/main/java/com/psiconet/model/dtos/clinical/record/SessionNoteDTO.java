package com.psiconet.model.dtos.clinical.record;

import tools.jackson.databind.JsonNode;
import com.psiconet.model.enums.clinical.SessionNoteStatusEnum;
import com.psiconet.model.enums.clinical.SessionNoteTypeEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SessionNoteDTO(
        UUID id,
        UUID appointmentId,
        LocalDateTime appointmentStart,
        SessionNoteTypeEnum type,
        SessionNoteStatusEnum status,
        String title,
        JsonNode content,
        String authorName,
        String authorCrp,
        String integrityHash,
        /** Hash recalculado confere com o gravado (somente para finalizadas). */
        boolean integrityVerified,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime finalizedAt,
        boolean editable,
        Long version,
        List<SessionNoteAddendumDTO> addenda
) {}
