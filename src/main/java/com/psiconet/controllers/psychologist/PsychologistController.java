package com.psiconet.controllers.psychologist;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.model.dtos.profile.BillingSettingsUpdateDTO;
import com.psiconet.model.dtos.profile.ProfileUpdateDTO;
import com.psiconet.model.dtos.profile.PsychologistMeDTO;
import com.psiconet.model.dtos.profile.PsychologistProfileDTO;
import com.psiconet.model.dtos.profile.SearchPsychologistDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.implement.profile.PsychologistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.PageImpl;
import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/psychologists")
@RequiredArgsConstructor
public class PsychologistController {

    private final PsychologistService service;

    @GetMapping("/me")
    public ResponseEntity<PsychologistMeDTO> getMyProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(service.getMyProfile(user));
    }

    @PatchMapping("/me/billing-settings")
    public ResponseEntity<Void> updateBillingSettings(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BillingSettingsUpdateDTO dto
    ) {
        service.updateBillingSettings(user, dto);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<Void> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ProfileUpdateDTO dto
    ) {
        service.updateProfile(user, dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/photo", consumes = "multipart/form-data")
    public ResponseEntity<PsychologistMeDTO> updatePhoto(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(service.updatePhoto(user, file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PsychologistProfileDTO> getProfile(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.getProfile(user, id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SearchPsychologistDTO>> search(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String crp,
            @PageableDefault(size = 10) Pageable pageable
    ) {

        if (crp != null && !crp.isBlank()) {
            SearchPsychologistDTO dto = service.searchByCrpWithStatus(user, crp);
            return ResponseEntity.ok(new PageImpl<>(Collections.singletonList(dto), pageable, 1));
        }

        if (name == null || name.isBlank()) {
            throw new BusinessException("search", "Informe ao menos um parâmetro: name ou crp.");
        }

        return ResponseEntity.ok(service.search(user, name, pageable));
    }
}
