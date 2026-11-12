package com.psiconet.infra.web;

import com.psiconet.infra.exceptions.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Confere {@code ?sort=} contra a lista de {@link SortableBy} antes da resolução dos argumentos.
 * Campo fora da lista ⇒ 400 com o erro no campo "sort".
 */
@Component
public class PageableSortGuard implements HandlerInterceptor {

    private static final Set<String> DIRECTIONS = Set.of("asc", "desc");

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String[] sortParams = request.getParameterValues("sort");
        if (sortParams == null || !(handler instanceof HandlerMethod method)) return true;

        Optional<MethodParameter> pageable = Arrays.stream(method.getMethodParameters())
                .filter(p -> Pageable.class.isAssignableFrom(p.getParameterType()))
                .findFirst();
        if (pageable.isEmpty()) return true;

        SortableBy sortable = pageable.get().getParameterAnnotation(SortableBy.class);
        Set<String> allowed = sortable == null ? Set.of() : Set.of(sortable.value());

        for (String param : sortParams) {
            for (String token : param.split(",")) {
                String property = token.trim();
                if (property.isEmpty() || DIRECTIONS.contains(property.toLowerCase())) continue;
                if (!allowed.contains(property)) {
                    throw new BusinessException("sort", allowed.isEmpty()
                            ? "Esta listagem não aceita ordenação personalizada."
                            : "Ordenação não permitida. Use: " + String.join(", ", allowed) + ".");
                }
            }
        }
        return true;
    }
}
