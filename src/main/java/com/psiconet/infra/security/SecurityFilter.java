package com.psiconet.infra.security;

import com.psiconet.infra.exceptions.ErrorResponse;
import com.psiconet.model.entities.access.User;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.services.interfaces.legal.ConsentService;
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

    // Liberadas com aceite de Termos/Privacidade pendente (ADR 013): é por elas que o aceite acontece.
    private static final Set<String> CONSENT_PENDING_ALLOWED = Set.of(
            "/auth/change-password", "/auth/logout", "/auth/logout-all", "/users/me",
            "/legal/consents", "/legal/consents/status", "/legal/consents/me"
    );

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final JsonSecurityErrorHandlers errorHandlers;
    private final ConsentService consentService;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Optional<TokenService.TokenClaims> claims = recoverToken(request).flatMap(tokenService::validate);
        Optional<User> authenticated = claims
                .flatMap(c -> userRepository.findByEmail(c.subject())
                        // Status, versão e sessão conferidos a CADA requisição: inativar/bloquear o usuário,
                        // incrementar tokenVersion (logout geral, troca de senha) ou encerrar a sessão do
                        // dispositivo (logout) invalida o token na hora.
                        .filter(User::isEnabled)
                        .filter(user -> user.getTokenVersion() == c.version())
                        .filter(user -> sessionService.isActive(c.sessionId(), user)));

        if (authenticated.isPresent()) {
            User user = authenticated.get();
            var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            // Id da sessão do dispositivo, usado pelo logout para encerrar só esta sessão.
            authentication.setDetails(new SessionDetails(claims.get().sessionId()));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            String path = pathWithinApplication(request);
            if (user.isMustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(path)) {
                errorHandlers.write(response, ErrorResponse.withCode(
                        HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                        "Você precisa definir uma nova senha antes de continuar."));
                return;
            }
            // Depois da troca de senha (se houver): Termos/Privacidade novos ou nunca aceitos bloqueiam o
            // restante da API até o aceite. Os documentos em si (/legal/documents) são públicos.
            if (consentService.isConsentRequired(user) && !isAllowedWithConsentPending(path)) {
                errorHandlers.write(response, ErrorResponse.withCode(
                        HttpServletResponse.SC_FORBIDDEN, "CONSENT_REQUIRED",
                        "Os Termos de Uso ou a Política de Privacidade foram atualizados. Leia e aceite para continuar."));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAllowedWithConsentPending(String path) {
        return CONSENT_PENDING_ALLOWED.contains(path) || path.equals("/legal/versions") || path.startsWith("/legal/documents/");
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
