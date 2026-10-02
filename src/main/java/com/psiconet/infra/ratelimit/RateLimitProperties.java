package com.psiconet.infra.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Limites de requisição — ÚNICO lugar de configuração ({@code application.properties}, prefixo
 * {@code psiconet.rate-limit}). Cada política define quantas requisições ({@code capacity}) são
 * aceitas por janela ({@code period}); a recarga é gradual (token bucket).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "psiconet.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Política usada quando o nome anotado não existe na configuração. */
    private Policy defaultPolicy = new Policy(120, Duration.ofMinutes(1));

    private Map<String, Policy> policies = new HashMap<>();

    public Policy policy(String name) {
        return policies.getOrDefault(name, defaultPolicy);
    }

    @Getter
    @Setter
    public static class Policy {
        private int capacity;
        private Duration period;

        public Policy() {}

        public Policy(int capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }
    }
}
