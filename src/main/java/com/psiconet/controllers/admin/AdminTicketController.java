package com.psiconet.controllers.admin;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.support.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.support.TicketCategoryEnum;
import com.psiconet.model.enums.support.TicketPriorityEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;
import com.psiconet.services.interfaces.support.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Atendimento de chamados pela administração. */
@RestController
@RequestMapping("/admin/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<Page<TicketSummaryDTO>> list(
            @RequestParam(required = false) TicketStatusEnum status,
            @RequestParam(required = false) TicketPriorityEnum priority,
            @RequestParam(required = false) TicketCategoryEnum category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.listForAdmin(status, priority, category, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getForAdmin(id));
    }

    @PostMapping("/{id}/messages")
    @RateLimited("ticket-message")
    public ResponseEntity<TicketDetailDTO> reply(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody TicketMessageCreateDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.replyAsAdmin(admin, id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketDetailDTO> updateStatus(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody TicketStatusUpdateDTO dto
    ) {
        return ResponseEntity.ok(ticketService.updateStatusAsAdmin(admin, id, dto.status()));
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<TicketDetailDTO> updatePriority(
            @AuthenticationPrincipal User admin,
            @PathVariable UUID id,
            @Valid @RequestBody TicketPriorityUpdateDTO dto
    ) {
        return ResponseEntity.ok(ticketService.updatePriorityAsAdmin(admin, id, dto.priority()));
    }
}
