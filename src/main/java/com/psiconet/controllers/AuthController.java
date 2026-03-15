package com.psiconet.controllers;

import com.psiconet.model.dtos.auth.AuthenticationDTO;
import com.psiconet.model.dtos.auth.LoginResponseDTO;
import com.psiconet.model.dtos.auth.PatientRegisterRequest;
import com.psiconet.model.dtos.auth.PsychologistRegisterRequest;
import com.psiconet.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register/patient")
    public ResponseEntity<String> registerPatient(@Valid @RequestBody PatientRegisterRequest data) {
        authService.registerPatient(data);
        return ResponseEntity.ok("Paciente cadastrado com sucesso!");
    }

    @PostMapping("/register/psychologist")
    public ResponseEntity<String> registerPsychologist(@Valid @RequestBody PsychologistRegisterRequest data) {
        authService.registerPsychologist(data);
        return ResponseEntity.ok("Psicólogo cadastrado com sucesso!");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody AuthenticationDTO data) {
        String token = authService.login(data);
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }
}