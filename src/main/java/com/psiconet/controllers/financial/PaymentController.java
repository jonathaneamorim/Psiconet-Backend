package com.psiconet.controllers.financial;

import org.springframework.security.access.prepost.PreAuthorize;
import com.psiconet.model.dtos.financial.FinancialSummaryDTO;
import com.psiconet.model.dtos.financial.PaymentDTO;
import com.psiconet.model.dtos.financial.PaymentDisputeDTO;
import com.psiconet.model.dtos.financial.PaymentRejectDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import com.psiconet.infra.storage.StoredFileType;
import com.psiconet.services.interfaces.financial.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping(value = "/{id}/receipt", consumes = "multipart/form-data")
    public ResponseEntity<PaymentDTO> uploadReceipt(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(paymentService.uploadReceipt(user, id, file));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<Resource> downloadReceipt(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        Resource resource = paymentService.downloadReceipt(user, id);
        // Tipo pela extensão que o próprio backend gravou (SEC-07). Desconhecido ⇒ download, nunca exibido.
        Optional<MediaType> known = StoredFileType.mediaTypeOf(resource.getFilename());
        ContentDisposition disposition = (known.isPresent() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename("comprovante" + extensionOf(resource.getFilename()))
                .build();

        return ResponseEntity.ok()
                .contentType(known.orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }

    @PreAuthorize("hasRole('PSYCHOLOGIST')")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<PaymentDTO> approve(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.approve(user, id));
    }

    @PreAuthorize("hasRole('PSYCHOLOGIST')")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<PaymentDTO> reject(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRejectDTO dto
    ) {
        return ResponseEntity.ok(paymentService.reject(user, id, dto));
    }

    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/{id}/dispute")
    public ResponseEntity<PaymentDTO> dispute(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody PaymentDisputeDTO dto
    ) {
        return ResponseEntity.ok(paymentService.dispute(user, id, dto));
    }

    @GetMapping
    public ResponseEntity<Page<PaymentDTO>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) PaymentStatusEnum status,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.list(user, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDTO> getById(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getById(user, id));
    }

    @GetMapping("/summary")
    public ResponseEntity<FinancialSummaryDTO> getFinancialSummary(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(paymentService.getFinancialSummary(user, year, month));
    }

    private static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,5}") ? ext : "";
    }
}
