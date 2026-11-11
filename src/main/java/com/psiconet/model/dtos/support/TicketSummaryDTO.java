package com.psiconet.model.dtos.support;

import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.support.TicketCategoryEnum;
import com.psiconet.model.enums.support.TicketPriorityEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;

import java.time.LocalDateTime;
import java.util.UUID;

/** Linha da listagem de chamados. {@code requesterName/requesterRole} só são preenchidos na visão admin. */
public record TicketSummaryDTO(
        UUID id,
        String protocol,
        TicketCategoryEnum category,
        String subject,
        TicketPriorityEnum priority,
        TicketStatusEnum status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastMessageAt,
        String requesterName,
        RoleEnum requesterRole
) {}
