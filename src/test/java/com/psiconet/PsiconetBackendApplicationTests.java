package com.psiconet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class PsiconetBackendApplicationTests {

    @Autowired
    private ConfigurableEnvironment environment;

    @Test
    void contextLoads() {
    }

    // O .env local do desenvolvedor não pode alterar o resultado dos testes (src/test/resources/.env.properties).
    @Test
    void envLocalNaoEntraNosTestes() {
        environment.getPropertySources().stream()
                // Variáveis reais do ambiente (ex.: CI) são legítimas; o que não pode é o arquivo .env.
                .filter(source -> !source.getName().startsWith("system"))
                .forEach(source -> {
                    // containsProperty: a falha nunca imprime o valor (é segredo).
                    assertFalse(source.containsProperty("JWT_SECRET"), "JWT_SECRET do .env carregado nos testes");
                    assertFalse(source.containsProperty("DB_URL"), "DB_URL do .env carregado nos testes");
                });
    }

}
