package com.psiconet.model.dtos.support;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mensagem do chamado. Para o usuário, mensagens da administração aparecem como "Equipe Psiconet"
 * (o nome do administrador só é exibido na visão admin).
 */
public record TicketMessageDTO(
        UUID id,
        String authorName,
        boolean fromSupport,
        boolean mine,
        String content,
        LocalDateTime createdAt
) {}
