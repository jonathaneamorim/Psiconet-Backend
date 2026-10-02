package com.psiconet.controllers.legal;

import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.legal.LegalDocument;
import com.psiconet.infra.legal.LegalDocumentRegistry;
import com.psiconet.infra.legal.LegalDocumentType;
import com.psiconet.model.dtos.legal.ConsentAcceptRequestDTO;
import com.psiconet.model.dtos.legal.ConsentRecordDTO;
import com.psiconet.model.dtos.legal.ConsentStatusDTO;
import com.psiconet.model.dtos.legal.LegalDocumentDTO;
import com.psiconet.model.dtos.legal.LegalVersionsDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.legal.ConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * Documentos legais (públicos) e consentimentos do usuário logado (ADRs 011 e 013).
 * {@code /legal/versions} e {@code /legal/documents/**} são públicos; o resto exige login e fica
 * liberado mesmo com aceite pendente (é por aqui que o aceite acontece).
 */
@RestController
@RequestMapping("/legal")
@RequiredArgsConstructor
public class LegalController {

    private final LegalDocumentRegistry registry;
    private final ConsentService consentService;

    @GetMapping("/versions")
    public ResponseEntity<LegalVersionsDTO> versions() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new LegalVersionsDTO(info(LegalDocumentType.TERMS), info(LegalDocumentType.PRIVACY), info(LegalDocumentType.COOKIES)));
    }

    @GetMapping("/documents/{slug}")
    public ResponseEntity<LegalDocumentDTO> document(@PathVariable String slug) {
        LegalDocumentType type = LegalDocumentType.fromSlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado."));
        LegalDocument doc = registry.get(type);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(new LegalDocumentDTO(type.name(), type.getSlug(), doc.title(), doc.version(), doc.effectiveDate(), doc.content()));
    }

    @GetMapping("/consents/status")
    public ResponseEntity<ConsentStatusDTO> status(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(consentService.status(user));
    }

    @GetMapping("/consents/me")
    public ResponseEntity<List<ConsentRecordDTO>> history(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(consentService.history(user));
    }

    @PostMapping("/consents")
    public ResponseEntity<ConsentStatusDTO> accept(@AuthenticationPrincipal User user, @Valid @RequestBody ConsentAcceptRequestDTO request) {
        return ResponseEntity.ok(consentService.accept(user, request));
    }

    private LegalVersionsDTO.VersionInfo info(LegalDocumentType type) {
        LegalDocument doc = registry.get(type);
        return new LegalVersionsDTO.VersionInfo(doc.version(), doc.effectiveDate());
    }
}
