package com.psiconet.controllers.clinical;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.clinical.RecurrenceOccurrenceOverlapDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.clinical.RecurrenceRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recurrence-rules")
@RequiredArgsConstructor
public class RecurrenceRuleController {

    private final RecurrenceRuleService recurrenceRuleService;

    @PreAuthorize("hasRole('PSYCHOLOGIST')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        recurrenceRuleService.deactivate(user, id);
        return ResponseEntity.noContent().build();
    }

    // Datas da série que coincidem com outra consulta, para marcar no calendário (ADR 019). Só o psicólogo.
    @PreAuthorize("hasRole('PSYCHOLOGIST')")
    @GetMapping("/{id}/preview/overlaps")
    @RateLimited("schedule-overlap-check")
    public ResponseEntity<List<RecurrenceOccurrenceOverlapDTO>> previewOverlaps(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(recurrenceRuleService.previewOverlaps(user, id, from, to));
    }

    // Calcula (sem persistir) as ocorrências futuras da regra, para o front renderizar no calendário.
    @GetMapping("/{id}/preview")
    public ResponseEntity<List<LocalDateTime>> preview(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(recurrenceRuleService.previewOccurrences(user, id, from, to));
    }
}
