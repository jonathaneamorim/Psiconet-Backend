package com.psiconet.controllers.financial;

import com.psiconet.model.dtos.financial.FinancialSummaryDTO;
import com.psiconet.model.dtos.financial.PaymentDTO;
import com.psiconet.model.dtos.financial.PaymentDisputeDTO;
import com.psiconet.model.dtos.financial.PaymentRejectDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.financial.PaymentStatusEnum;
import com.psiconet.services.interfaces.financial.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

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
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<PaymentDTO> approve(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.approve(user, id));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<PaymentDTO> reject(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRejectDTO dto
    ) {
        return ResponseEntity.ok(paymentService.reject(user, id, dto));
    }

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
}
