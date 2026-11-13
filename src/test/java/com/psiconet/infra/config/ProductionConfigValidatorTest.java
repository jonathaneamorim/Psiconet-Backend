package com.psiconet.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fase 4 (x): em produção a API não sobe sem segredos e URLs públicas. */
class ProductionConfigValidatorTest {

    private MockEnvironment complete() {
        return new MockEnvironment()
                .withProperty("spring.datasource.password", "x")
                .withProperty("psiconet.clinical.master-keys", "v1:abc")
                .withProperty("psiconet.clinical.active-key-version", "v1")
                .withProperty("psiconet.mail.from", "nao-responda@psiconet.com.br")
                .withProperty("spring.mail.username", "u")
                .withProperty("spring.mail.password", "p")
                .withProperty("psiconet.frontend-url", "https://app.psiconet.com.br")
                .withProperty("psiconet.backend-url", "https://api.psiconet.com.br")
                .withProperty("psiconet.cors.allowed-origins", "https://app.psiconet.com.br")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update");
    }

    @Test
    void configuracaoCompletaPassa() {
        assertThat(ProductionConfigValidator.problems(complete())).isEmpty();
    }

    @Test
    void semSegredosOuComUrlsLocaisNaoSobe() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("psiconet.frontend-url", "http://localhost:3000")
                .withProperty("psiconet.cors.allowed-origins", "*")
                .withProperty("spring.jpa.hibernate.ddl-auto", "create-drop")
                .withProperty("springdoc.api-docs.enabled", "true");

        assertThat(ProductionConfigValidator.problems(env))
                .anyMatch(p -> p.contains("banco"))
                .anyMatch(p -> p.contains("CLINICAL_MASTER_KEYS"))
                .anyMatch(p -> p.contains("MAIL_FROM"))
                .anyMatch(p -> p.contains("FRONTEND_URL"))
                .anyMatch(p -> p.contains("BACKEND_URL"))
                .anyMatch(p -> p.contains("CORS"))
                .anyMatch(p -> p.contains("create-drop"))
                .anyMatch(p -> p.contains("API_DOCS_ENABLED"));

        ProductionConfigValidator validator = new ProductionConfigValidator(env);
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class)
                // nunca imprime valores, só nomes
                .hasMessageNotContaining("localhost:3000");
    }
}
