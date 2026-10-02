package com.psiconet.services.interfaces.support;

import com.psiconet.model.dtos.support.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.support.TicketCategoryEnum;
import com.psiconet.model.enums.support.TicketPriorityEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TicketService {

    // --- Usuário (paciente/psicólogo): sempre restrito aos próprios chamados ---
    TicketDetailDTO open(User requester, TicketCreateDTO dto);
    Page<TicketSummaryDTO> listMine(User requester, TicketStatusEnum status, Pageable pageable);
    TicketDetailDTO getMine(User requester, UUID ticketId);
    TicketDetailDTO replyAsRequester(User requester, UUID ticketId, TicketMessageCreateDTO dto);
    TicketDetailDTO closeByRequester(User requester, UUID ticketId);

    // --- Administração ---
    Page<TicketSummaryDTO> listForAdmin(TicketStatusEnum status, TicketPriorityEnum priority,
                                        TicketCategoryEnum category, String search, Pageable pageable);
    TicketDetailDTO getForAdmin(UUID ticketId);
    TicketDetailDTO replyAsAdmin(User admin, UUID ticketId, TicketMessageCreateDTO dto);
    TicketDetailDTO updateStatusAsAdmin(User admin, UUID ticketId, TicketStatusEnum status);
    TicketDetailDTO updatePriorityAsAdmin(User admin, UUID ticketId, TicketPriorityEnum priority);
}
