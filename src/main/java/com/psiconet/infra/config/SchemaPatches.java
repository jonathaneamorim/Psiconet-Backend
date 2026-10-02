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
            "ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_type_check",
            // Mesmo motivo: nova categoria de chamado PRIVACY (Privacidade / LGPD).
            "ALTER TABLE IF EXISTS chamado DROP CONSTRAINT IF EXISTS chamado_categoria_check",

            // SessionNote deixou de ser @OneToOne com a consulta: remove a UNIQUE(agendamento_id) gerada
            // pelo Hibernate (nome hash) — permite registro documental + anotação pessoal na mesma consulta.
            """
            DO $$ DECLARE c record; BEGIN
              FOR c IN SELECT con.conname FROM pg_constraint con
                       JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = ANY(con.conkey)
                       WHERE con.conrelid = 'anotacoes_sessao'::regclass AND con.contype = 'u'
                         AND a.attname = 'agendamento_id' AND array_length(con.conkey, 1) = 1
              LOOP EXECUTE format('ALTER TABLE anotacoes_sessao DROP CONSTRAINT %I', c.conname); END LOOP;
            END $$
            """,

            // --- Imutabilidade e "sem exclusão física" garantidas também no banco (defesa em profundidade) ---
            """
            CREATE OR REPLACE FUNCTION psiconet_bloqueia_alteracao() RETURNS trigger AS $$
            BEGIN RAISE EXCEPTION 'Operação não permitida: registro imutável em %', TG_TABLE_NAME; END;
            $$ LANGUAGE plpgsql
            """,
            """
            CREATE OR REPLACE FUNCTION psiconet_bloqueia_anotacao_finalizada() RETURNS trigger AS $$
            BEGIN
              IF OLD.situacao = 'FINALIZED' THEN
                RAISE EXCEPTION 'Anotação finalizada é imutável; registre um adendo.';
              END IF;
              RETURN NEW;
            END;
            $$ LANGUAGE plpgsql
            """,
            "DROP TRIGGER IF EXISTS trg_auditoria_clinica_imutavel ON auditoria_clinica",
            "CREATE TRIGGER trg_auditoria_clinica_imutavel BEFORE UPDATE OR DELETE ON auditoria_clinica FOR EACH ROW EXECUTE FUNCTION psiconet_bloqueia_alteracao()",
            "DROP TRIGGER IF EXISTS trg_adendo_imutavel ON anotacao_adendo",
            "CREATE TRIGGER trg_adendo_imutavel BEFORE UPDATE OR DELETE ON anotacao_adendo FOR EACH ROW EXECUTE FUNCTION psiconet_bloqueia_alteracao()",
            "DROP TRIGGER IF EXISTS trg_log_alteracao_imutavel ON log_alteracao",
            "CREATE TRIGGER trg_log_alteracao_imutavel BEFORE UPDATE OR DELETE ON log_alteracao FOR EACH ROW EXECUTE FUNCTION psiconet_bloqueia_alteracao()",
            // Prova de aceite dos documentos legais: só inserção (ADR 013)
            "DROP TRIGGER IF EXISTS trg_consentimento_imutavel ON consentimento_usuario",
            "CREATE TRIGGER trg_consentimento_imutavel BEFORE UPDATE OR DELETE ON consentimento_usuario FOR EACH ROW EXECUTE FUNCTION psiconet_bloqueia_alteracao()",
            "DROP TRIGGER IF EXISTS trg_prontuario_sem_exclusao ON prontuario",
            "CREATE TRIGGER trg_prontuario_sem_exclusao BEFORE DELETE ON prontuario FOR EACH ROW EXECUTE FUNCTION psiconet_bloqueia_alteracao()",
            "DROP TRIGGER IF EXISTS trg_anotacao_sem_exclusao ON anotacoes_sessao",
            "CREATE TRIGGER trg_anotacao_sem_exclusao BEFORE DELETE ON anotacoes_sessao FOR EACH ROW EXECUTE FUNCTION psiconet_bloqueia_alteracao()",
            "DROP TRIGGER IF EXISTS trg_anotacao_finalizada ON anotacoes_sessao",
            "CREATE TRIGGER trg_anotacao_finalizada BEFORE UPDATE ON anotacoes_sessao FOR EACH ROW EXECUTE FUNCTION psiconet_bloqueia_anotacao_finalizada()"
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
