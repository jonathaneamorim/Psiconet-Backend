package com.psiconet.controllers.support;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.support.TicketCreateDTO;
import com.psiconet.model.dtos.support.TicketDetailDTO;
import com.psiconet.model.dtos.support.TicketMessageCreateDTO;
import com.psiconet.model.dtos.support.TicketSummaryDTO;
import com.psiconet.model.entities.access.User;
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

/** Chamados de suporte do próprio usuário (paciente ou psicólogo). */
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PATIENT', 'PSYCHOLOGIST')")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    @RateLimited("ticket-open")
    public ResponseEntity<TicketDetailDTO> open(@AuthenticationPrincipal User user, @Valid @RequestBody TicketCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.open(user, dto));
    }

    @GetMapping
    public ResponseEntity<Page<TicketSummaryDTO>> listMine(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) TicketStatusEnum status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.listMine(user, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailDTO> get(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getMine(user, id));
    }

    @PostMapping("/{id}/messages")
    @RateLimited("ticket-message")
    public ResponseEntity<TicketDetailDTO> reply(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody TicketMessageCreateDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.replyAsRequester(user, id, dto));
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<TicketDetailDTO> close(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.closeByRequester(user, id));
    }
}
