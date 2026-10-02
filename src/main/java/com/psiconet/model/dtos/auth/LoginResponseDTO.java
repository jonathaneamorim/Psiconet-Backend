package com.psiconet.model.dtos.auth;

import com.psiconet.model.enums.RoleEnum;

import java.time.Instant;

/**
 * Resposta de login, renovação e troca de senha (ADR 014).
 * <ul>
 *   <li>{@code token}/{@code expiresAt}: token de acesso curto (cookie {@code psiconet_token}).</li>
 *   <li>{@code refreshToken}/{@code refreshExpiresAt}: refresh rotativo (cookie {@code psiconet_refresh});
 *       {@code refreshToken} nulo = manter o cookie atual. Sem {@code rememberMe}, o front grava os
 *       dois como cookies de sessão do navegador.</li>
 *   <li>{@code mustChangePassword}: senha temporária — o backend bloqueia o resto até a troca.</li>
 *   <li>{@code consentRequired}: Termos/Privacidade pendentes de (re)aceite (ADR 013).</li>
 * </ul>
 */
public record LoginResponseDTO(
        String token,
        Instant expiresAt,
        String refreshToken,
        Instant refreshExpiresAt,
        boolean rememberMe,
        RoleEnum role,
        boolean mustChangePassword,
        boolean consentRequired
) {}
