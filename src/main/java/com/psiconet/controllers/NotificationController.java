package com.psiconet.controllers;

import com.psiconet.model.dtos.NotificationDTO;
import com.psiconet.model.dtos.NotificationPreferenceDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.listForUser(user.getId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> countUnread(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.countUnread(user.getId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        service.markAsRead(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceDTO> getPreferences(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.getPreferences(user));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<NotificationPreferenceDTO> updatePreferences(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NotificationPreferenceDTO dto
    ) {
        return ResponseEntity.ok(service.updatePreferences(user, dto));
    }
}
