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

    /** IP da requisição HTTP corrente, ou {@code null} fora de uma requisição (jobs agendados). */
    public String currentRequestIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return resolve(attributes.getRequest());
        }
        return null;
    }
}
