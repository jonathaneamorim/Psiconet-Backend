package com.psiconet.controllers;

import com.psiconet.model.dtos.profile.PatientMeDTO;
import com.psiconet.model.dtos.profile.PatientProfileDTO;
import com.psiconet.model.dtos.profile.PatientProfileUpdateDTO;
import com.psiconet.model.dtos.profile.SearchPatientDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.implement.profile.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping("/me")
    public ResponseEntity<PatientMeDTO> getMyProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(patientService.getMyProfile(user));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<Void> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PatientProfileUpdateDTO dto
    ) {
        patientService.updateProfile(user, dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/photo", consumes = "multipart/form-data")
    public ResponseEntity<PatientMeDTO> updatePhoto(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(patientService.updatePhoto(user, file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientProfileDTO> getProfile(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(patientService.getProfile(user, id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SearchPatientDTO>> search(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String name,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        if (name == null || name.isBlank()) {
            throw new com.psiconet.infra.exceptions.BusinessException("search", "Informe o nome para a busca.");
        }
        return ResponseEntity.ok(patientService.search(user, name, pageable));
    }
}
