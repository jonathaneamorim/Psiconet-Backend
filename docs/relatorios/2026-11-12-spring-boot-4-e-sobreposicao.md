# Relatório — Spring Boot 4.1 + sobreposição de agendamentos (2026-11-12)

Branches (sem push): backend `chore/spring-boot-4` (Parte A) → `feat/sobreposicao-agendamento` (Parte B);
frontend `feat/sobreposicao-agendamento`. Ambas partem de `fix/auditoria-seguranca` (ainda não mesclada na `main`).
ADRs: 018 (versões) e 019 (regra de sobreposição).

## 1. Versões — antes × depois

| Componente | Antes | Depois |
|---|---|---|
| Spring Boot | 3.3.5 (OSS encerrado em 30/06/2025) | **4.1.2** (OSS até 31/07/2027) |
| Spring Framework / Security | 6.1.14 / 6.3.4 | 7.x / 7.x |
| Hibernate ORM / Spring Data | 6.5 / 2024.0 | 7.x / 2025.x |
| Jackson | 2.17.2 | **3.1** (`tools.jackson`) |
| Tomcat | 10.1.31 | 11 |
| Java (alvo / imagem) | 17 / `eclipse-temurin:17-jre` | **25 LTS** / `eclipse-temurin:25-jre` |
| springdoc | 2.6.0 | 3.2.0 |
| spring-dotenv | `spring-dotenv` 4.0.0 | `springboot4-dotenv` 5.1.0 |
| Lombok / MapStruct / java-jwt | 1.18.42 / 1.5.5 / 4.4.0 | 1.18.48 / 1.6.3 / 4.6.2 |
| Maven (wrapper / imagem de build) | 3.9.12 / 3.9.9 | 3.9.16 / 3.9.16 |

Tags de retorno: `pre-upgrade-spring`, `upgrade-0-baseline`, `upgrade-1-boot-3.5.16`, `upgrade-2-boot-4.1.2`, `upgrade-3-java-25`.

## 2. Erros encontrados na troca e correção

| Etapa | Erro | Correção |
|---|---|---|
| 3.5 | `AntPathRequestMatcher` depreciado (removido no Security 7) | `PathPatternRequestMatcher` |
| 3.5 | `@MockBean` depreciado (removido no Boot 4) | `@MockitoBean` |
| 3.5 | `MethodValidationResult.getAllValidationResults()` depreciado | `getParameterValidationResults()` |
| 4.1 | `package org.springframework.boot.test.autoconfigure.web.servlet does not exist` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` + starter `spring-boot-starter-webmvc-test` |
| 4.1 | `MockMultipartHttpServletRequestBuilder cannot be converted to MockHttpServletRequestBuilder` (Spring 7) | helper genérico sobre `AbstractMockHttpServletRequestBuilder` |
| 4.1 | Contexto não sobe: `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'` (o Jackson 2 compila — vem pelo java-jwt — mas o Boot 4 só registra o Jackson 3) | migração para `tools.jackson` em 20 arquivos; `JacksonException`; `asString()` |
| 4.1 | spring-dotenv 4.x sem suporte à Boot 4 | `springboot4-dotenv` 5.1.0; testes isolados com `springdotenv.enabled=false` no Surefire |
| 4.1 | springdoc 2.x incompatível | springdoc 3.2.0 (matriz oficial) |
| Java 25 | `asText()` depreciado (Jackson 3); `HttpStatus.PAYLOAD_TOO_LARGE` depreciado; `lombok` declarado 2× no pom | `asString()`; `CONTENT_TOO_LARGE` (o código `PAYLOAD_TOO_LARGE` do contrato não muda); duplicata removida |
| Validação | ognl 3.4 (corrige o único aviso restante) quebra o Thymeleaf 3.1 (`NoSuchMethodError OgnlContext`) | mantido 3.3.4 — risco aceito no ADR 018 |

Nenhuma propriedade renomeada (`spring-boot-properties-migrator`, temporário, removido). Nenhuma `@Query` precisou mudar.

## 3. Parte C — evidências

| Item | Resultado |
|---|---|
| C1 backend `./mvnw clean verify` | ✅ 266 testes, 0 falhas, 0 avisos de compilação/depreciação |
| C1 frontend lint / tsc / test / build | ✅ 0 erros (40 avisos antigos) / ok / 157 testes / ok |
| C2 contrato JSON | ✅ 30 respostas idênticas 3.5.16 × 4.1.2 (referência gerada na 3.5 em worktree); Parte B: só `hasOverlap`/`overlappingAppointments` nas 2 respostas de agenda do psicólogo |
| C2 OpenAPI | ✅ 89 caminhos/operações iguais; diferenças só descritivas (OpenAPI 3.1.0, `PagedModel` por DTO, `format: email`, `LocalTime` string) |
| Banco | ✅ 4.1.2 sobe com `ddl-auto=validate` na cópia do banco; com `update`, única DDL = `CREATE INDEX idx_agendamento_vinculo_inicio` |
| C3 fumaça integrada (API real + Postgres + Mailpit + LiveKit) | ✅ **43/43**: cadastro, login com "manter conectado", refresh, recuperação de senha com e-mail capturado (+ "senha alterada"), avatar, conexão/vínculo, consulta, sobreposição (pré-checagem, 409, confirmação, selo, privacidade do paciente, mesmo paciente bloqueado, cancelamento remove alerta), série com confirmação, pagamento (enviar/rejeitar/reenviar/aprovar/baixar), notificações, chamado + resposta do admin com e-mail, painel admin, token de sala de vídeo, CORS, cabeçalhos, rate limit |
| C3 frontend | ✅ build apontando para a API 4.1.2; páginas públicas 200, rota protegida → login, Termos renderizados com o documento servido pela API. ⚠ Fluxo de clique no navegador não executado — coberto pelos testes de componente |
| C4 logs | ✅ inicialização sem WARN/ERROR; na fumaça só 3 avisos de negócio esperados; 0 e-mails completos e 0 tokens no log; jobs sem erro |
| C5 segurança | ✅ matriz de autorização e testes da auditoria verdes; OSV: **100 vulnerabilidades (13 críticas) → 1 moderada** (ognl) |
| C6 desempenho (mediana de 4 execuções, cópia do banco) | consultas iguais (±3 ms); inicialização 7,5 s → 10,0 s local (7,4 s na imagem JRE 25) |
| Docker | ✅ `docker build` (testes no JDK 25.0.5 da imagem), usuário `psiconet`, healthcheck 200, `docker compose config` válido |

## 4. Decisões que dependem do dono do produto
- **ADR 019 §2:** mesmo paciente sobreposto continua bloqueado (proposta).
- **ADR 019 §3:** só `ACCEPTED` não terminadas geram alerta (histórico não).
- Notificação `SCHEDULE_OVERLAP` respeita a preferência "agendamento" (poderia ser obrigatória).
- Pendências anteriores: `NO_SHOW`, estorno de consulta paga cancelada, consentimento de saúde do psicólogo.

## 5. Riscos residuais e próximos passos
- ognl 3.3.4 (moderada) até o Thymeleaf 3.2; acompanhar.
- Inicialização ~2,5 s mais lenta localmente — investigar se virar problema (ex.: `spring.jackson.find-and-add-modules`).
- Acompanhar a 4.2 (GA prevista nov/2026) e o fim do OSS da 4.1 (31/07/2027); a 4.0 encerra em 31/12/2026.
- Automatizar: Dependabot/Renovate, OSV/`npm audit` na CI, `ApiContractSnapshotTest` como teste de contrato da CI.
- Flyway segue pendente (ADR 001); ShedLock antes de mais de uma instância.
- **Regressão da auditoria corrigida aqui:** o teto de página 100 (SEC-08) cortava telas que pedem 200 → teto 200
  (`79e13b5`, também aplicado em `fix/auditoria-seguranca`).

## 6. Rollback
`git checkout upgrade-1-boot-3.5.16` (Boot 3.5, Java 17) ou `pre-upgrade-spring`, rebuild da imagem. Sem mudança de
banco a desfazer (o índice novo é inofensivo e pode ficar). Parte B: reverter `db03329` (backend) e `852115a` (front).
