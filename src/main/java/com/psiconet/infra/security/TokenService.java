package com.psiconet.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.psiconet.model.entities.access.User;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class TokenService {

    private static final String ISSUER = "psiconet";
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${api.security.token.secret:}")
    private String secret;

    @Value("${api.security.token.expiration_hours:2}")
    private Integer expirationHours;

    @Value("${api.security.token.remember-me-hours:168}")
    private Integer rememberMeHours;

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

    public IssuedToken generateToken(User user, boolean rememberMe) {
        Instant expiresAt = Instant.now().plus(rememberMe ? rememberMeHours : expirationHours, ChronoUnit.HOURS);
        try {
            String token = JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getEmail())
                    .withClaim("role", user.getRole().toString())
                    .withClaim("ver", user.getTokenVersion())
                    .withIssuedAt(Instant.now())
                    .withExpiresAt(expiresAt)
                    .sign(algorithm);
            return new IssuedToken(token, expiresAt);
        } catch (JWTCreationException exception) {
            throw new IllegalStateException("Erro ao gerar token JWT", exception);
        }
    }

    public Optional<TokenClaims> validate(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).withIssuer(ISSUER).build().verify(token);
            Integer version = jwt.getClaim("ver").asInt();
            return Optional.of(new TokenClaims(jwt.getSubject(), version == null ? 0 : version));
        } catch (JWTVerificationException exception) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    public record TokenClaims(String subject, int version) {}
}
