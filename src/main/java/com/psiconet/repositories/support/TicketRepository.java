package com.psiconet.repositories.support;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.support.Ticket;
import com.psiconet.model.enums.support.TicketStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    @EntityGraph(attributePaths = {"requester"})
    Optional<Ticket> findWithRequesterById(UUID id);

    // Anti-IDOR: busca sempre restrita ao dono
    @EntityGraph(attributePaths = {"requester"})
    Optional<Ticket> findByIdAndRequester(UUID id, User requester);

    Page<Ticket> findByRequester(User requester, Pageable pageable);

    Page<Ticket> findByRequesterAndStatus(User requester, TicketStatusEnum status, Pageable pageable);

    long countByRequesterAndStatusIn(User requester, Collection<TicketStatusEnum> statuses);

    Optional<Ticket> findFirstByRequesterOrderByCreatedAtDesc(User requester);

    boolean existsByRequesterAndContentHashAndCreatedAtAfter(User requester, String contentHash, LocalDateTime after);

    long countByStatus(TicketStatusEnum status);

    boolean existsByProtocol(String protocol);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();
}
