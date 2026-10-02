package com.psiconet.repositories.support;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.support.Ticket;
import com.psiconet.model.entities.support.TicketMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

    @EntityGraph(attributePaths = {"author"})
    List<TicketMessage> findByTicketOrderByCreatedAtAsc(Ticket ticket);

    boolean existsByTicketAndAuthorAndContentHashAndCreatedAtAfter(
            Ticket ticket, User author, String contentHash, LocalDateTime after);
}
