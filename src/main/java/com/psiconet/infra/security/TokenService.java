package com.psiconet.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.psiconet.model.entities.access.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Emite e valida o token de ACESSO (JWT HS256, curto). A sessão longa é o refresh (SessionService). */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ISSUER = "psiconet";
    private static final int MIN_SECRET_LENGTH = 32;

    private final SessionProperties sessionProperties;

    @Value("${api.security.token.secret:}")
    private String secret;

    private Algorithm algorithm;

    // Falha rápido: sem segredo (ou com segredo curto) a aplicação não sobe. Antes havia um
    // fallback hardcoded no application.properties — qualquer um com acesso ao repositório
    // conseguia forjar tokens.
    @PostConstruct
    void init() {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET não configurado ou com menos de " + MIN_SECRET_LENGTH + " caracteres. " +
                    "Defina a variável de ambiente JWT_SECRET (ex.: openssl rand -base64 48).");
        }
        algorithm = Algorithm.HMAC256(secret);
    }

    public IssuedToken generateAccessToken(User user, UUID sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionProperties.getAccessToken());
        try {
            String token = JWT.create()
                    .withIssuer(ISSUER)
                    // Id imutável, não o e-mail: o payload do JWT é legível por quem tem o token (SEC-12).
                    .withSubject(user.getId().toString())
                    .withClaim("role", user.getRole().toString())
                    .withClaim("ver", user.getTokenVersion())
                    .withClaim("sid", sessionId.toString())
                    .withIssuedAt(now)
                    .withExpiresAt(expiresAt)
                    .sign(algorithm);
            return new IssuedToken(token, expiresAt);
        } catch (JWTCreationException exception) {
            throw new IllegalStateException("Erro ao gerar token JWT", exception);
        }
    }

    /** Assinatura, emissor e expiração conferidos; token sem {@code sid} (formato antigo) é recusado. */
    public Optional<TokenClaims> validate(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).withIssuer(ISSUER).build().verify(token);
            Integer version = jwt.getClaim("ver").asInt();
            String sid = jwt.getClaim("sid").asString();
            if (sid == null) return Optional.empty();
            return Optional.of(new TokenClaims(UUID.fromString(jwt.getSubject()), version == null ? 0 : version, UUID.fromString(sid)));
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    public record TokenClaims(UUID userId, int version, UUID sessionId) {}
}
