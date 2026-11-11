package com.psiconet.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Detalhes da autenticação: id da sessão (dispositivo) do token de acesso em uso. */
public record SessionDetails(UUID sessionId) {

    public static Optional<UUID> currentSessionId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof SessionDetails details
                ? Optional.of(details.sessionId()) : Optional.empty();
    }
}
