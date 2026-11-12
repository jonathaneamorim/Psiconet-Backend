package com.psiconet.model.dtos.clinical.record;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionNoteAddendumDTO(
        UUID id,
        int sequence,
        String reason,
        JsonNode content,
        String authorName,
        String authorCrp,
        String integrityHash,
        boolean integrityVerified,
        LocalDateTime createdAt
) {}
