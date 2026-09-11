package com.psiconet.controllers.clinical;

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

    @PatchMapping("/{id}/price")
    public ResponseEntity<Void> updatePrice(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody TreatmentLinkPriceUpdateDTO dto
    ) {
        treatmentLinkService.updateDefaultPrice(user, id, dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/recurrence-rules")
    public ResponseEntity<RecurrenceRuleDTO> createRecurrenceRule(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody RecurrenceRuleCreateDTO dto
    ) {
        RecurrenceRuleDTO created = recurrenceRuleService.create(user, id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
