package com.psiconet.infra.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplica a política de rate limit indicada (configurada em {@code psiconet.rate-limit.policies.<nome>})
 * ao endpoint. O limite é contado por IP e, quando há usuário autenticado, também por usuário.
 * Excedido: HTTP 429 com cabeçalho {@code Retry-After}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    /** Nome da política em {@code psiconet.rate-limit.policies}. */
    String value();
}
