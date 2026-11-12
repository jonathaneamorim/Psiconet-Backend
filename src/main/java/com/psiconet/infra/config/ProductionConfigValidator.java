package com.psiconet.infra.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perfil {@code prod} (SPRING_PROFILES_ACTIVE=prod): a API não sobe com configuração insegura ou
 * incompleta (SEC-01, SEC-02, SEC-15, BUG-06). A mensagem cita só NOMES de variáveis, nunca valores.
 * JWT_SECRET já é exigido sempre pelo TokenService; o admin base, pelo DataInitializer.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionConfigValidator {

    private final Environment environment;

    @PostConstruct
    void validate() {
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Configuração de produção inválida — corrija antes de subir:\n - "
                    + String.join("\n - ", problems));
        }
        log.info("Configuração de produção conferida.");
    }

    static List<String> problems(Environment env) {
        List<String> problems = new ArrayList<>();

        if (blank(env, "spring.datasource.password")) {
            problems.add("Senha do banco não definida (DB_PASSWORD ou SPRING_DATASOURCE_PASSWORD).");
        }
        if (blank(env, "psiconet.clinical.master-keys") || blank(env, "psiconet.clinical.active-key-version")) {
            problems.add("CLINICAL_MASTER_KEYS/CLINICAL_ACTIVE_KEY_VERSION não definidos (prontuário ficaria indisponível).");
        }
        if (blank(env, "psiconet.mail.from") || blank(env, "spring.mail.username") || blank(env, "spring.mail.password")) {
            problems.add("E-mail não configurado (MAIL_FROM, MAIL_USERNAME, MAIL_PASSWORD): redefinição de senha não funcionaria.");
        }
        requirePublicHttps(env, "psiconet.frontend-url", "FRONTEND_URL", problems);
        requirePublicHttps(env, "psiconet.backend-url", "BACKEND_URL", problems);

        String origins = env.getProperty("psiconet.cors.allowed-origins", "");
        if (origins.contains("*")) {
            problems.add("CORS_ALLOWED_ORIGINS não pode conter '*'.");
        }

        String ddl = env.getProperty("spring.jpa.hibernate.ddl-auto", "").trim().toLowerCase(Locale.ROOT);
        if (ddl.startsWith("create")) {
            problems.add("SPRING_JPA_HIBERNATE_DDL_AUTO=" + ddl + " apaga o schema; use update (aditivo) ou validate.");
        }
        if (env.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
            problems.add("API_DOCS_ENABLED=true expõe a documentação da API; desligue em produção.");
        }
        if (!env.getProperty("psiconet.rate-limit.enabled", Boolean.class, true)) {
            problems.add("RATE_LIMIT_ENABLED=false desliga a proteção contra força bruta.");
        }
        return problems;
    }

    private static void requirePublicHttps(Environment env, String property, String variable, List<String> problems) {
        String url = env.getProperty(property, "").trim().toLowerCase(Locale.ROOT);
        if (!url.startsWith("https://") || url.contains("localhost") || url.contains("127.0.0.1")) {
            problems.add(variable + " deve ser a URL pública com https:// (links de e-mail e fotos usam esse endereço).");
        }
    }

    private static boolean blank(Environment env, String property) {
        String value = env.getProperty(property);
        return value == null || value.isBlank();
    }
}
