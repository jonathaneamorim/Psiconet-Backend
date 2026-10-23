package com.psiconet.infra.video;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Videochamada integrada (psiconet.video.*) — ver docs/decisoes/016-videochamada-integrada.md.
 * Segredos (chave e segredo do provedor) vêm só de variáveis de ambiente; sem eles a funcionalidade
 * fica desligada mesmo com {@code enabled=true}.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "psiconet.video")
public class VideoProperties {

    /** Feature flag. Desligada (ou sem credenciais) ⇒ "Sala Psiconet" some e o link externo segue funcionando. */
    private boolean enabled = false;

    /** Implementação de {@link VideoProvider} a usar. Hoje só existe "livekit". */
    private String provider = "livekit";

    /** URL que o navegador usa para conectar (ex.: wss://projeto.livekit.cloud). */
    private String url;

    /** URL HTTP(S) da API do servidor; vazia ⇒ derivada de {@link #url} (wss→https, ws→http). */
    private String apiUrl;

    private String apiKey;
    private String apiSecret;

    /** Validade do token de entrada (só serve para conectar; o provedor renova a sessão já conectada). */
    private Duration tokenTtl = Duration.ofMinutes(10);

    /** A sala abre este tempo antes do início da consulta... */
    private Duration joinOpensBefore = Duration.ofMinutes(30);

    /** ...e fecha este tempo depois do fim. */
    private Duration joinClosesAfter = Duration.ofMinutes(15);

    /** Antecedência do lembrete (notificação + e-mail). */
    private Duration reminderBefore = Duration.ofMinutes(15);

    /**
     * Sala criada na entrada, mas em que ninguém chegou a conectar, é descartada após este tempo.
     * (A sala nasce quando alguém entra; uma nova entrada a recria.)
     */
    private Duration emptyTimeout = Duration.ofMinutes(1);

    /**
     * Depois que todos saem, a sala é fechada após este tempo (folga para recarregar a página).
     * O LiveKit trata 0 como "padrão do servidor" (20 s), por isso o valor nunca é zero.
     */
    private Duration departureTimeout = Duration.ofSeconds(5);

    /** Quem fica sozinho na sala por este tempo é desconectado (pelo front; o backend garante com folga). */
    private Duration aloneTimeout = Duration.ofMinutes(15);

    /**
     * Limite de horário: a sala é encerrada para todos este tempo após o FIM da consulta (nunca antes do
     * fechamento da entrada). Evita sessões indefinidas consumindo o provedor.
     */
    private Duration hardStopAfterEnd = Duration.ofMinutes(60);

    /** Disponível de fato: flag ligada e provedor configurado. */
    public boolean isAvailable() {
        return enabled && hasText(url) && hasText(apiKey) && hasText(apiSecret);
    }

    public String resolvedApiUrl() {
        if (hasText(apiUrl)) return stripTrailingSlash(apiUrl);
        if (!hasText(url)) return null;
        String base = url.startsWith("wss://") ? "https://" + url.substring(6)
                : url.startsWith("ws://") ? "http://" + url.substring(5)
                : url;
        return stripTrailingSlash(base);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
