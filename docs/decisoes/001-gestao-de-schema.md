# ADR 001 — Gestão de schema do banco

- **Status:** aceito
- **Data:** 2026-10-01

## Contexto
O schema é gerido por `spring.jpa.hibernate.ddl-auto=update`; não há Flyway/Liquibase. O banco de
desenvolvimento tem dados reais de teste (usuários, conexões, agendamentos). A evolução exige novas
tabelas (chamados, prontuário, auditoria) e novas colunas em tabelas existentes (`usuario`,
`anotacoes_sessao`, `log_alteracao`).

## Decisão
1. **Manter `ddl-auto=update` nesta entrega**, usando somente mudanças **aditivas**: novas tabelas e
   novas colunas. Colunas novas em tabelas com dados usam `@ColumnDefault` (o Hibernate gera
   `ADD COLUMN ... DEFAULT x NOT NULL`, válido no Postgres com linhas existentes) ou são anuláveis.
2. **Nenhuma coluna é removida ou renomeada.** Colunas legadas de `SessionNote`
   (`resumo`, `estado_mental`, `tarefas_casa`) e `MedicalRecord` permanecem; os dados são migrados em
   código, de forma idempotente, para o novo formato criptografado (ver ADR 008) e as colunas antigas
   são esvaziadas só depois de a cópia criptografada ser gravada e conferida.
3. Testes passam a rodar em **H2 em memória** (perfil `test`), nunca no Postgres de desenvolvimento.
4. Backup do banco de dev feito antes da evolução (`pg_dump`), fora do repositório.

## Recomendação (backlog)
Adotar **Flyway** antes de produção: gerar o baseline do schema atual (`pg_dump --schema-only`),
`spring.flyway.baseline-on-migrate=true`, e trocar `ddl-auto` para `validate`. Migrações versionadas
são revisáveis em PR e reversíveis por script; `update` não remove nada, mas também não versiona.

## Consequências
- Sem risco de perda de dados nesta entrega; rollback = voltar o código (colunas novas ficam ociosas).
- Restrições como `NOT NULL` em colunas antigas não são alteradas pelo Hibernate — documentadas
  como débito para a migração Flyway.
