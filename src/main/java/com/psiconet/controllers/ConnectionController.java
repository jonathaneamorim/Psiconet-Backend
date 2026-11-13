package com.psiconet.controllers;

import com.psiconet.infra.web.SortableBy;
import com.psiconet.model.dtos.profile.ActiveConnectionDTO;
import com.psiconet.model.dtos.profile.ConnectionDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.profile.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/connections")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @PostMapping("/{targetUserId}")
    public ResponseEntity<Void> sendRequest(
            @AuthenticationPrincipal User user,
            @PathVariable UUID targetUserId
    ) {
        connectionService.sendRequest(user, targetUserId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<Void> acceptRequest(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        connectionService.acceptRequest(user, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<Void> rejectRequest(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        connectionService.rejectRequest(user, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeConnection(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        connectionService.removeConnection(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pending")
    public ResponseEntity<Page<ConnectionDTO>> listPendingRequests(
            @AuthenticationPrincipal User user,
            @SortableBy({"requestedAt"}) @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(connectionService.listPendingRequests(user, pageable));
    }

    /** Solicitações que o usuário enviou e ainda aguardam resposta (cancelar = DELETE /connections/{id}). */
    @GetMapping("/sent")
    public ResponseEntity<Page<ConnectionDTO>> listSentRequests(
            @AuthenticationPrincipal User user,
            @SortableBy({"requestedAt"}) @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(connectionService.listSentRequests(user, pageable));
    }

    @GetMapping
    public ResponseEntity<Page<ActiveConnectionDTO>> listAcceptedConnections(
            @AuthenticationPrincipal User user,
            // "connectedAt" é traduzido para acceptedAt no service
            @SortableBy({"connectedAt"}) @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(connectionService.listAcceptedConnections(user, pageable));
    }
}
