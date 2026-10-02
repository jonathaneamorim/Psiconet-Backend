package com.psiconet.controllers.clinical;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.clinical.record.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.clinical.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Prontuário e anotações de sessão — exclusivo do PSICÓLOGO titular. Administradores não acessam
 * (nem via suporte). Respostas com Cache-Control: no-store (ClinicalNoStoreFilter).
 */
@RestController
@RequestMapping("/medical-records")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PSYCHOLOGIST')")
public class MedicalRecordController {

    private final MedicalRecordService service;

    @GetMapping
    public ResponseEntity<List<MedicalRecordSummaryDTO>> listMine(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.listMine(user));
    }

    @GetMapping("/links/{linkId}")
    public ResponseEntity<MedicalRecordDTO> get(@AuthenticationPrincipal User user, @PathVariable UUID linkId) {
        return ResponseEntity.ok(service.get(user, linkId));
    }

    @PutMapping("/links/{linkId}")
    @RateLimited("clinical-write")
    public ResponseEntity<MedicalRecordDTO> update(@AuthenticationPrincipal User user, @PathVariable UUID linkId,
                                                   @Valid @RequestBody MedicalRecordUpdateDTO dto) {
        return ResponseEntity.ok(service.update(user, linkId, dto));
    }

    @GetMapping("/links/{linkId}/notes")
    public ResponseEntity<List<SessionNoteDTO>> listNotes(@AuthenticationPrincipal User user, @PathVariable UUID linkId) {
        return ResponseEntity.ok(service.listNotes(user, linkId));
    }

    @PostMapping("/links/{linkId}/notes")
    @RateLimited("clinical-write")
    public ResponseEntity<SessionNoteDTO> createNote(@AuthenticationPrincipal User user, @PathVariable UUID linkId,
                                                     @Valid @RequestBody SessionNoteCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createNote(user, linkId, dto));
    }

    @GetMapping("/links/{linkId}/audit")
    public ResponseEntity<Page<ClinicalAuditEntryDTO>> audit(@AuthenticationPrincipal User user, @PathVariable UUID linkId,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.auditTrail(user, linkId, pageable));
    }

    @GetMapping("/notes/{noteId}")
    public ResponseEntity<SessionNoteDTO> getNote(@AuthenticationPrincipal User user, @PathVariable UUID noteId) {
        return ResponseEntity.ok(service.getNote(user, noteId));
    }

    /** Autosave do rascunho (somente DRAFT, somente o autor). */
    @PutMapping("/notes/{noteId}/draft")
    @RateLimited("clinical-write")
    public ResponseEntity<SessionNoteDTO> saveDraft(@AuthenticationPrincipal User user, @PathVariable UUID noteId,
                                                    @Valid @RequestBody SessionNoteDraftUpdateDTO dto) {
        return ResponseEntity.ok(service.saveDraft(user, noteId, dto));
    }

    @PostMapping("/notes/{noteId}/finalize")
    @RateLimited("clinical-write")
    public ResponseEntity<SessionNoteDTO> finalizeNote(@AuthenticationPrincipal User user, @PathVariable UUID noteId) {
        return ResponseEntity.ok(service.finalizeNote(user, noteId));
    }

    @PostMapping("/notes/{noteId}/addenda")
    @RateLimited("clinical-write")
    public ResponseEntity<SessionNoteDTO> addAddendum(@AuthenticationPrincipal User user, @PathVariable UUID noteId,
                                                      @Valid @RequestBody SessionNoteAddendumCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addAddendum(user, noteId, dto));
    }

    @GetMapping("/access-requests")
    public ResponseEntity<List<AccessRequestDTO>> accessRequests(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.listAccessRequests(user));
    }

    @PostMapping("/access-requests/{requestId}/grant")
    @RateLimited("clinical-write")
    public ResponseEntity<AccessRequestDTO> grant(@AuthenticationPrincipal User user, @PathVariable UUID requestId,
                                                  @Valid @RequestBody AccessGrantDTO dto) {
        return ResponseEntity.ok(service.grant(user, requestId, dto));
    }

    @PostMapping("/access-requests/{requestId}/deny")
    @RateLimited("clinical-write")
    public ResponseEntity<AccessRequestDTO> deny(@AuthenticationPrincipal User user, @PathVariable UUID requestId,
                                                 @Valid @RequestBody AccessDenyDTO dto) {
        return ResponseEntity.ok(service.deny(user, requestId, dto));
    }
}
