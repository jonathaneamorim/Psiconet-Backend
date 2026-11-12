package com.psiconet.controllers.clinical;

import java.util.List;
import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.clinical.RecurrenceOccurrenceOverlapDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import com.psiconet.model.dtos.clinical.RecurrenceRuleCreateDTO;
import com.psiconet.model.dtos.clinical.RecurrenceRuleDTO;
import com.psiconet.model.dtos.clinical.TreatmentLinkPriceUpdateDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.clinical.RecurrenceRuleService;
import com.psiconet.services.interfaces.clinical.TreatmentLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/treatment-links")
@RequiredArgsConstructor
public class TreatmentLinkController {

    private final TreatmentLinkService treatmentLinkService;
    private final RecurrenceRuleService recurrenceRuleService;

    @PreAuthorize("hasRole('PSYCHOLOGIST')")
    @PatchMapping("/{id}/price")
    public ResponseEntity<Void> updatePrice(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody TreatmentLinkPriceUpdateDTO dto
    ) {
        treatmentLinkService.updateDefaultPrice(user, id, dto);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('PSYCHOLOGIST')")
    @PostMapping("/{id}/recurrence-rules")
    public ResponseEntity<RecurrenceRuleDTO> createRecurrenceRule(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody RecurrenceRuleCreateDTO dto
    ) {
        RecurrenceRuleDTO created = recurrenceRuleService.create(user, id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Rascunho da série: quais ocorrências dos próximos 90 dias coincidem com outra consulta (ADR 019).
    @PreAuthorize("hasRole('PSYCHOLOGIST')")
    @PostMapping("/{id}/recurrence-rules/overlaps")
    @RateLimited("schedule-overlap-check")
    public ResponseEntity<List<RecurrenceOccurrenceOverlapDTO>> draftRecurrenceOverlaps(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody RecurrenceRuleCreateDTO dto
    ) {
        return ResponseEntity.ok(recurrenceRuleService.draftOverlaps(user, id, dto));
    }

    @GetMapping("/{id}/recurrence-rules")
    public ResponseEntity<Page<RecurrenceRuleDTO>> listRecurrenceRules(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(recurrenceRuleService.list(user, id, pageable));
    }
}
