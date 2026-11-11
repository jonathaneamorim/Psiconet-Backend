package com.psiconet.infra.security;

/** Sessão expirada, revogada ou refresh inválido. Respondido como 401 {@code SESSION_EXPIRED}. */
public class InvalidSessionException extends RuntimeException {
    public InvalidSessionException() {
        super("Sua sessão expirou. Entre novamente.");
    }
}
