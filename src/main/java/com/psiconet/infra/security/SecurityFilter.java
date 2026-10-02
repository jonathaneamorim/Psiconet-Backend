package com.psiconet.infra.security;

import com.psiconet.infra.exceptions.ErrorResponse;
import com.psiconet.model.entities.access.User;
import com.psiconet.repositories.access.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    // Rotas liberadas enquanto o usuário tem troca de senha obrigatória pendente.
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "/auth/change-password", "/auth/logout", "/users/me"
    );

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final JsonSecurityErrorHandlers errorHandlers;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Optional<User> authenticated = recoverToken(request)
                .flatMap(tokenService::validate)
                .flatMap(claims -> userRepository.findByEmail(claims.subject())
                        // Status e versão conferidos a CADA requisição: inativar/bloquear o usuário ou
                        // incrementar tokenVersion (logout, troca de senha) invalida o token na hora.
                        .filter(User::isEnabled)
                        .filter(user -> user.getTokenVersion() == claims.version()));

        if (authenticated.isPresent()) {
            User user = authenticated.get();
            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (user.isMustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(pathWithinApplication(request))) {
                errorHandlers.write(response, ErrorResponse.withCode(
                        HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                        "Você precisa definir uma nova senha antes de continuar."));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private Optional<String> recoverToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
