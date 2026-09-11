package com.psiconet.model.dtos;

import com.psiconet.model.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationDTO(
        UUID id,
        String title,
        String message,
        NotificationType type,
        String referenceType,
        UUID referenceId,
        boolean isRead,
        LocalDateTime createdAt
) {}
