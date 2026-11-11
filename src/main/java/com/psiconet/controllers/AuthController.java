package com.psiconet.controllers;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.MessageResponseDTO;
import com.psiconet.model.dtos.auth.AuthenticationDTO;
import com.psiconet.model.dtos.auth.ChangePasswordRequestDTO;
import com.psiconet.model.dtos.auth.ForgotPasswordRequestDTO;
import com.psiconet.model.dtos.auth.LoginResponseDTO;
import com.psiconet.model.dtos.auth.PatientRegisterRequest;
import com.psiconet.model.dtos.auth.PsychologistRegisterRequest;
import com.psiconet.model.dtos.auth.RefreshRequestDTO;
import com.psiconet.model.dtos.auth.RegisterResponseDTO;
import com.psiconet.model.dtos.auth.ResetPasswordRequestDTO;
import com.psiconet.model.dtos.auth.ValidateResetTokenRequestDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.services.implement.AuthService;
import com.psiconet.services.interfaces.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register/patient")
    @RateLimited("register")
    public ResponseEntity<RegisterResponseDTO> registerPatient(@Valid @RequestBody PatientRegisterRequest data) {
        authService.registerPatient(data);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponseDTO("Conta de paciente criada com sucesso! Faça login para continuar.", "LOGIN"));
    }

    @PostMapping("/register/psychologist")
    @RateLimited("register")
    public ResponseEntity<RegisterResponseDTO> registerPsychologist(@Valid @RequestBody PsychologistRegisterRequest data) {
        authService.registerPsychologist(data);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponseDTO("Conta de psicólogo(a) criada com sucesso! Faça login para continuar.", "LOGIN"));
    }

    @PostMapping("/login")
    @RateLimited("login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody AuthenticationDTO data) {
        return ResponseEntity.ok(authService.login(data));
    }

    /** Renova a sessão (refresh rotativo, ADR 014). Chamado pelo servidor do front (proxy.ts). */
    @PostMapping("/refresh")
    @RateLimited("refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO data) {
        return ResponseEntity.ok(authService.refresh(data.refreshToken()));
    }

    /** Encerra a sessão deste dispositivo. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        authService.logout(user);
        return ResponseEntity.noContent().build();
    }

    /** Encerra as sessões de todos os dispositivos. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal User user) {
        authService.logoutAll(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @RateLimited("change-password")
    public ResponseEntity<LoginResponseDTO> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequestDTO data
    ) {
        return ResponseEntity.ok(authService.changePassword(user, data));
    }

    @PostMapping("/forgot-password")
    @RateLimited("forgot-password")
    public ResponseEntity<MessageResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO data) {
        passwordResetService.requestReset(data.email());
        return ResponseEntity.ok(new MessageResponseDTO(
                "Se o e-mail informado estiver cadastrado, você receberá um link de redefinição em instantes."));
    }

    @PostMapping("/reset-password/validate")
    @RateLimited("reset-password")
    public ResponseEntity<Void> validateResetToken(@Valid @RequestBody ValidateResetTokenRequestDTO data) {
        passwordResetService.validateResetToken(data.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @RateLimited("reset-password")
    public ResponseEntity<MessageResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO data) {
        passwordResetService.confirmReset(data.token(), data.newPassword());
        return ResponseEntity.ok(new MessageResponseDTO("Senha redefinida com sucesso."));
    }
}
