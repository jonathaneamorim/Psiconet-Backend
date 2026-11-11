package com.psiconet.model.dtos.support;

import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.support.TicketCategoryEnum;
import com.psiconet.model.enums.support.TicketPriorityEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TicketDetailDTO(
        UUID id,
        String protocol,
        TicketCategoryEnum category,
        String subject,
        String description,
        TicketPriorityEnum priority,
        TicketStatusEnum status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        String requesterName,
        String requesterEmail,
        RoleEnum requesterRole,
        boolean canReply,
        List<TicketMessageDTO> messages
) {}
