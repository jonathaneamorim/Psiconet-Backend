package com.psiconet.controllers;

import com.psiconet.model.dtos.auth.AuthenticationDTO;
import com.psiconet.model.dtos.auth.ForgotPasswordRequestDTO;
import com.psiconet.model.dtos.auth.LoginResponseDTO;
import com.psiconet.model.dtos.auth.PatientRegisterRequest;
import com.psiconet.model.dtos.auth.PsychologistRegisterRequest;
import com.psiconet.model.dtos.auth.ResetPasswordRequestDTO;
import com.psiconet.model.dtos.auth.ValidateResetTokenRequestDTO;
import com.psiconet.services.implement.AuthService;
import com.psiconet.services.interfaces.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register/patient")
    public ResponseEntity<String> registerPatient(@Valid @RequestBody PatientRegisterRequest data) {
        System.out.println(data);
        authService.registerPatient(data);
        return ResponseEntity.status(HttpStatus.CREATED).body("Paciente cadastrado com sucesso!");
    }

    @PostMapping("/register/psychologist")
    public ResponseEntity<String> registerPsychologist(@Valid @RequestBody PsychologistRegisterRequest data) {
        authService.registerPsychologist(data);
        return ResponseEntity.status(HttpStatus.CREATED).body("Psicólogo cadastrado com sucesso!");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody AuthenticationDTO data) {
        String token = authService.login(data);
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO data) {
        passwordResetService.requestReset(data.email());
        return ResponseEntity.ok("Se o e-mail informado estiver cadastrado, você receberá um link de redefinição em instantes.");
    }

    @PostMapping("/reset-password/validate")
    public ResponseEntity<Void> validateResetToken(@Valid @RequestBody ValidateResetTokenRequestDTO data) {
        passwordResetService.validateResetToken(data.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO data) {
        passwordResetService.confirmReset(data.token(), data.newPassword());
        return ResponseEntity.ok("Senha redefinida com sucesso.");
    }
}