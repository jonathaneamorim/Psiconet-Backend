package com.psiconet.infra.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Durações da sessão — ÚNICA fonte de verdade (psiconet.session.*, ADR 014). O frontend não tem
 * durações próprias: grava os cookies com as expirações devolvidas pela API.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "psiconet.session")
public class SessionProperties {
    /** Validade do token de acesso (JWT). Curta: a sessão continua via refresh. */
    private Duration accessToken = Duration.ofMinutes(15);
    /** Sem "manter conectado": expira após este tempo sem uso... */
    private Duration idle = Duration.ofHours(2);
    /** ...ou neste tempo total, o que vier primeiro. */
    private Duration absolute = Duration.ofHours(12);
    /** Com "manter conectado": tempo sem uso até expirar (renovado a cada uso)... */
    private Duration rememberIdle = Duration.ofDays(7);
    /** ...e limite absoluto, depois do qual é preciso entrar de novo. */
    private Duration rememberAbsolute = Duration.ofDays(30);
    /** Janela em que o refresh token recém-rotacionado ainda é aceito (requisições paralelas). */
    private Duration reuseGrace = Duration.ofSeconds(30);
}
