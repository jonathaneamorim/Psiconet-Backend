package com.psiconet.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
// Habilita @PreAuthorize nos controllers/services (defesa em profundidade junto às regras de rota).
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Filtro responsável por validar o token JWT em cada requisição
    private final SecurityFilter securityFilter;
    private final JsonSecurityErrorHandlers errorHandlers;

    // Origens permitidas para chamadas diretas do navegador. O frontend chama a API pelo servidor
    // (Server Actions), então em geral só a própria URL do frontend precisa estar aqui.
    @Value("${psiconet.cors.allowed-origins:${psiconet.frontend-url}}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Ativação do contrato no spring
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Cabeçalhos de segurança (ADR 015). A API só devolve JSON/arquivos: CSP que não permite
                // nada (exceto no Swagger, que precisa de scripts próprios), sem referrer e sem APIs
                // sensíveis do navegador. X-Content-Type-Options, X-Frame-Options e Cache-Control já
                // vêm do Spring Security; HSTS é enviado pelo Spring quando a requisição chega por HTTPS.
                .headers(headers -> headers
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(new OrRequestMatcher(
                                        new AntPathRequestMatcher("/swagger-ui/**"),
                                        new AntPathRequestMatcher("/swagger-ui.html"),
                                        new AntPathRequestMatcher("/v3/api-docs/**"))),
                                new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")))
                )

                // Desativa CSRF pois estamos usando API stateless com JWT
                .csrf(AbstractHttpConfigurer::disable)

                // Define que a aplicação não usará sessão (stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Falhas de autenticação/autorização em JSON (401 sem token válido, 403 sem permissão)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(errorHandlers.accessDeniedHandler())
                )

                // Configuração de autorização das rotas
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Rotas de autenticação que exigem usuário logado
                        .requestMatchers("/auth/change-password", "/auth/logout-all").authenticated()

                        // Demais rotas de autenticação são públicas (logout é idempotente sem token)
                        .requestMatchers("/auth/**").permitAll()

                        // Arquivos públicos (ex.: fotos de perfil) — precisam funcionar em <img src>
                        // sem token, então ficam fora da autenticação.
                        .requestMatchers(HttpMethod.GET, "/public/**").permitAll()

                        // Documentos legais (Termos, Privacidade, Cookies) e suas versões: públicos (ADR 011)
                        .requestMatchers(HttpMethod.GET, "/legal/versions", "/legal/documents/**").permitAll()

                        // Webhook do provedor de vídeo: sem JWT de usuário, validado pela assinatura do
                        // provedor no próprio endpoint (ADR 016). Só POST.
                        .requestMatchers(HttpMethod.POST, "/webhooks/video").permitAll()

                        // Rotas do Swagger
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Página de erro interna do Spring (evita 401 mascarando o erro real)
                        .requestMatchers("/error").permitAll()

                        // Rotas administrativas protegidas
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Outras rotas precisam de autenticação
                        .anyRequest().authenticated()
                )

                // Adiciona nosso filtro JWT antes do filtro padrão de autenticação
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // Bean necessário para permitir uso do AuthenticationManager no AuthService
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // Bean responsável por criptografar senhas
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
