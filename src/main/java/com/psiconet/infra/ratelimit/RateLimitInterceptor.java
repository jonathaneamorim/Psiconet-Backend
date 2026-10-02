package com.psiconet.infra.ratelimit;

import com.psiconet.infra.web.ClientIpResolver;
import com.psiconet.model.entities.access.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica {@link RateLimited}: conta por IP (sempre) e por usuário autenticado (quando houver).
 * Roda depois da cadeia do Spring Security, então o usuário do JWT já está disponível.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;

        RateLimited limit = method.getMethodAnnotation(RateLimited.class);
        if (limit == null) return true;

        rateLimiter.consume(limit.value(), "ip:" + clientIpResolver.resolve(request));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            rateLimiter.consume(limit.value(), "user:" + user.getId());
        }
        return true;
    }
}
