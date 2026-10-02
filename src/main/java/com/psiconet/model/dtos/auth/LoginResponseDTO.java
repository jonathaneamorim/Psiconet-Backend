package com.psiconet.model.dtos.auth;

import com.psiconet.model.enums.RoleEnum;

import java.time.Instant;

/**
 * Resposta do login. {@code mustChangePassword} indica senha temporária: o frontend deve levar o
 * usuário à tela de troca de senha (o backend bloqueia as demais rotas enquanto isso).
 */
public record LoginResponseDTO(
        String token,
        Instant expiresAt,
        RoleEnum role,
        boolean mustChangePassword
) {}
