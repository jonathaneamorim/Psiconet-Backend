package com.psiconet.controllers.clinical;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.clinical.record.AccessRequestDTO;
import com.psiconet.model.dtos.clinical.record.PatientRecordLinkDTO;
import com.psiconet.model.dtos.clinical.record.PatientRecordViewDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.clinical.PatientMedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Acesso do PACIENTE ao próprio prontuário: solicitar, consultar o que foi liberado e exportar. */
@RestController
@RequestMapping("/patient/medical-records")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientMedicalRecordController {

    private final PatientMedicalRecordService service;

    @GetMapping
    public ResponseEntity<List<PatientRecordLinkDTO>> listMyLinks(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.listMyLinks(user));
    }

    @PostMapping("/links/{linkId}/access-requests")
    @RateLimited("record-access-request")
    public ResponseEntity<AccessRequestDTO> request(@AuthenticationPrincipal User user, @PathVariable UUID linkId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestAccess(user, linkId));
    }

    @GetMapping("/access-requests/{requestId}/record")
    public ResponseEntity<PatientRecordViewDTO> view(@AuthenticationPrincipal User user, @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.viewGranted(user, requestId));
    }

    @GetMapping("/access-requests/{requestId}/pdf")
    @RateLimited("clinical-write")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal User user, @PathVariable UUID requestId) {
        PatientMedicalRecordService.ExportedPdf pdf = service.exportPdf(user, requestId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .header("X-Document-SHA256", pdf.sha256())
                .body(pdf.content());
    }
}
