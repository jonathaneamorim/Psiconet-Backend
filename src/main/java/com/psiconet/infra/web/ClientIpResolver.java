package com.psiconet.infra.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolve o IP real do cliente. O frontend (Next.js) chama a API pelo servidor, então o
 * {@code remoteAddr} costuma ser o próprio servidor do front; nesse caso o front repassa o IP do
 * navegador em {@code X-Forwarded-For}. Esse cabeçalho só é aceito quando a conexão vem de um
 * proxy confiável (lista configurável) — senão qualquer cliente poderia forjar o próprio IP e
 * escapar do rate limit.
 */
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @Value("${psiconet.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") List<String> trustedProxies
    ) {
        this.trustedProxies = trustedProxies.stream().map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) {
            return remote;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }

        // Percorre da direita para a esquerda: o primeiro IP não confiável é o cliente real.
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                return hop.length() > 45 ? hop.substring(0, 45) : hop;
            }
        }
        return remote;
    }

    /**
     * Navegador resumido do cliente. Como o front chama a API pelo servidor, o User-Agent real do
     * navegador chega em {@code X-Client-User-Agent} — aceito, como o X-Forwarded-For, apenas de
     * proxy confiável. Retorna só o resumo ({@link UserAgentSummarizer}).
     */
    public String resolveUserAgent(HttpServletRequest request) {
        String forwarded = trustedProxies.contains(request.getRemoteAddr()) ? request.getHeader("X-Client-User-Agent") : null;
        return UserAgentSummarizer.summarize(forwarded != null && !forwarded.isBlank() ? forwarded : request.getHeader("User-Agent"));
    }

    public String currentRequestUserAgent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return resolveUserAgent(attributes.getRequest());
        }
        return null;
    }

    /** IP da requisição HTTP corrente, ou {@code null} fora de uma requisição (jobs agendados). */
    public String currentRequestIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return resolve(attributes.getRequest());
        }
        return null;
    }
}
