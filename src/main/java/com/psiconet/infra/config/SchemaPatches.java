package com.psiconet.infra.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * Ajustes de schema que o {@code ddl-auto=update} do Hibernate não faz (ele só cria tabelas e
 * colunas). Cada comando é IDEMPOTENTE e NÃO DESTRUTIVO (apenas amplia tipos/relaxa restrições).
 * Roda só no PostgreSQL; nos testes (H2) o schema é criado do zero a partir das entidades.
 * Substituir por migrações Flyway quando adotado (ADR 001).
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaPatches implements CommandLineRunner {

    private static final List<String> PATCHES = List.of(
            // Comunicados do admin podem passar de 255 caracteres (ampliação, sem perda de dados)
            "ALTER TABLE IF EXISTS notifications ALTER COLUMN message TYPE varchar(2000)",
            // O Hibernate 6 cria CHECK com os valores do enum na criação da tabela e nunca o atualiza:
            // sem isso, os novos tipos (chamados, comunicados, prontuário) seriam recusados pelo banco.
            // A validação do valor continua garantida pelo enum Java (@Enumerated STRING).
            "ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_type_check"
    );

    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String patch : PATCHES) {
            try {
                jdbc.execute(patch);
            } catch (Exception e) {
                log.warn("Ajuste de schema não aplicado ({}): {}", patch, e.getClass().getSimpleName());
            }
        }
    }
}
