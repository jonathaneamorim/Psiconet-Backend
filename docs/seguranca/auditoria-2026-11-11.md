# Auditoria de segurança e bugs — Backend (2026-11-11)

> Relatório vivo. Branch `fix/auditoria-seguranca` (a partir de `main` @ `dc0b5ee`). Decisões: ADR 017.
> Frontend: `../Psiconet-Frontend/docs/seguranca/auditoria-2026-11-11.md`. Matriz: `matriz-de-autorizacao.md`.

## Linha de base (antes)

| Verificação | Resultado |
|---|---|
| `./mvnw test` | 206 testes, **2 falhas** (`VideoSessionIntegrationTest`: `.env` local vazando nos testes + teste desatualizado) |
| Dependências | Spring Boot 3.3.5 — linha 3.3 **fora do suporte OSS** (fim em 2025-06); Spring Security 6.3.4 afetado pela classe do CVE-2025-22228 (senha > 72 bytes) — mitigado na aplicação (SEC-11) |

## Depois

| Verificação | Resultado |
|---|---|
| `./mvnw test` | **256 testes, 0 falhas** |
| `docker build` | ok (testes rodam no build; imagem JRE, usuário `psiconet`, TZ -03) |

## Achados

Status: **corrigido** · **já corrigido** (resolvido antes desta auditoria; verificado) · **não procede** · **parcial** · **pendente**.

| ID | Sev. | Arquivo(s) | Descrição | Status | Correção / commit | Teste |
|---|---|---|---|---|---|---|
| AUD-00 | Média | `src/test/resources` | `.env` do desenvolvedor carregado nos testes (spring-dotenv) mudava o resultado da suíte | corrigido | `236b6e6` | `PsiconetBackendApplicationTests.envLocalNaoEntraNosTestes` |
| SEC-01 | P0 | `application.properties` | Segredos com valor padrão | já corrigido (sem padrão; `TokenService` exige ≥ 32) + perfil prod valida o restante | `562df22` | `ProductionConfigValidatorTest` |
| SEC-02 | P0 | `docker-compose.prod.yml` | Variáveis do compose × propriedades | já corrigido (`JWT_SECRET`, `ADMIN_*` mapeados); adicionados perfil prod, TZ, volume de uploads | `562df22` | `docker compose config` |
| SEC-03 | P0 | `DataInitializer` | Admin com credencial/CPF fixos | já corrigido (env, política, troca obrigatória); em prod, ausência impede subir. CPF `00000000000`: **não procede** — sentinela inválida que nunca colide com CPF real | `562df22` | `AdminRulesIntegrationTest` |
| SEC-04 | P0 | `SecurityConfig` | CORS `*` | já corrigido (origens por `FRONTEND_URL`); prod recusa `*` | `562df22` | `ProductionConfigValidatorTest` |
| SEC-05 | P0 | `SecurityFilter` | Token válido p/ inativo; 403 anônimo | já corrigido (status/versão/sessão a cada requisição; 401/403 JSON) | — | `AuthControllerIntegrationTest.tokenDeixaDeValerQuandoUsuarioEhInativado` |
| SEC-06 | P0 | auth | Sem rate limit | já corrigido (ADR 004) | — | `RateLimitIntegrationTest` |
| SEC-07 | P0 | `LocalFileStorageServiceImpl`, `PublicFileController`, `PaymentController` | PNG chamado `x.html` gravado como `.html` e servido como `text/html` | **corrigido** — tipo/extensão do conteúdo validado, imagens regravadas (sem EXIF), legado não servido. "Avatar aceita PDF": não procede | `5747513` | `FileUploadSecurityIntegrationTest` (7) |
| SEC-08 | P1 | controllers paginados | `?size=` até 2000 e `?sort=` livre (ordenar por CPF do paciente era aceito) | **corrigido** — teto 100, `@SortableBy` | `227d354` | `PaginationGuardIntegrationTest` |
| SEC-09 | P1 | `GlobalExceptionHandler` | Erros do cliente como 500 | parcial já corrigido; **corrigidos** 415, 406, multipart sem arquivo, `@Validated`, `IllegalArgumentException` ecoando mensagem interna, mês 13 nas estatísticas | `f68aba5` | `ErrorHandlingIntegrationTest` (10) |
| SEC-10 | P1 | entidades | PII em `toString` | `User` já corrigido; **corrigidos** `Patient`/`Psychologist` (chave Pix, endereço, User lazy) | `4d1374a` | `EntityToStringTest` |
| SEC-11 | P1 | DTOs de senha | Política inconsistente | já unificada; **corrigido** limite de 72 bytes (BCrypt) com `@StrongPassword` | `eb6bf6c` | `AuthControllerIntegrationTest.senhaComMaisDe72Bytes…`, `PasswordResetIntegrationTest` |
| SEC-12 | P1 | `TokenService` | Sem revogação; e-mail como subject | revogação já existia (ADR 014); **corrigido** subject = id | `1d2b111` | `AuthControllerIntegrationTest.tokenNaoCarregaEmail…` |
| SEC-13 | P1 | services clínicos/financeiros | IDOR respondido com 400 "sem permissão" | **corrigido** — 404 terceiro / 403 papel; papel por rota + `@PreAuthorize` | `57595c8` | `AuthorizationMatrixIntegrationTest` (8) |
| SEC-14 | P1 | `AuthService` | Enumeração no login | já corrigido (senha antes do status, hash fictício). Cadastro revela duplicidade: **aceito** por UX, mitigado por rate limit (ADR 002/004) | — | `AuthControllerIntegrationTest.contaInativaSoRevelaStatusComSenhaCorreta` |
| SEC-15 | P2 | springdoc | Swagger público | **corrigido** — desligado por padrão (`API_DOCS_ENABLED`) e proibido em prod | `562df22` | `ProductionConfigValidatorTest` |
| SEC-16 | P2 | schema | `ddl-auto=update` em prod | **pendente** — prod recusa `create*`; Flyway com baseline precisa do dump do banco real (ADR 001) | — | — |
| SEC-17 | P2 | admin | Salvaguardas | já corrigido (ADR 006) | — | `AdminRulesIntegrationTest` |
| SEC-18 | P2 | cabeçalhos | Sem cabeçalhos | já corrigido (ADR 015); comprovante com `no-store` | `5747513` | `FileUploadSecurityIntegrationTest` (nosniff) |
| SEC-19 | P2 | `Dockerfile` | Root, JDK, `-DskipTests` | **corrigido** — JRE, não-root, testes no build, HEALTHCHECK, `.dockerignore` | `562df22` | `docker build` |
| SEC-20 | P2 | jobs | Concorrência | **corrigido** na recorrência (trava da regra; duplicação reproduzida 2/2); pagamento já único por consulta; ShedLock: decisão no ADR 017 | `4a44768` | `RecurrenceConcurrencyIntegrationTest` |
| SEC-22 | P3 | repositório | Higiene | **corrigido** — `uploads/` (fotos e **comprovantes**) e `HELP.md` deixam de ser versionados (histórico não reescrito) | `c5e588d` | — |
| SEC-23 | P3 | docs | Segredos em docs | não procede (revisados `CLAUDE.md`, `.env.example`, ADRs) | — | — |
| BUG-01 | — | cadastro | Texto puro quebrando o front | já corrigido (ADR 002) | — | `AuthControllerIntegrationTest.cadastroNovoRetornaJson…` |
| BUG-02 | — | login | `trim` na senha | já corrigido (front só "trima" e-mail) | — | `AuthControllerIntegrationTest.senhaComEspacosNasPontas…` |
| BUG-03 | — | erros | `errors` por campo | já corrigido; mensagens do Bean Validation agora em pt-BR | `f68aba5` | `ErrorHandlingIntegrationTest.mensagensDeValidacaoEmPortugues` |
| BUG-05 | — | `PasswordResetServiceImpl` | Redefinição incompleta | **corrigido** — e-mail "senha alterada", só conta ativa, limpeza diária, leitura sem escrita | `e0980aa` | `PasswordResetIntegrationTest` (6) |
| BUG-06 | — | `EmailDispatcher` | Sem nova tentativa | **corrigido** — até 3 tentativas com espera; config validada em prod. HTML+texto e log mascarado já existiam | `e2ecc79` | `EmailRetryIntegrationTest` |
| BUG-07a | — | agendamento | Paciente com consultas sobrepostas | **não procede por decisão** (bloquear revelaria tratamento com outro profissional) — ADR 017 | — | — |
| BUG-07b | — | agendamento | Sem limites | **corrigido** — 15 min–8 h, até 12 meses | `1dc3349` | `AppointmentLimitsIntegrationTest` |
| BUG-07c | — | fuso | `LocalDateTime` sem fuso | **parcial** — TZ `America/Sao_Paulo` na imagem e no compose; o front exibe a hora de parede do servidor (ver relatório do front) | `562df22` | — |
| BUG-07d | — | cancelamento | Cobrança após cancelar | já corrigido (cobrança não aprovada vira `CANCELLED`); **aprovada exige estorno manual** | — | — |
| BUG-07e | — | `NO_SHOW` | Nenhum fluxo define | **pendente** (decisão de produto: quem marca e efeito na cobrança) | — | — |
| BUG-07f | — | recorrência | Idempotência | **corrigido** (ver SEC-20) | `4a44768` | `RecurrenceConcurrencyIntegrationTest` |
| BUG-07g | — | estatísticas | Mês/ano | **corrigido** em `/appointments/stats` (resumo financeiro já validava) | `f68aba5` | `ErrorHandlingIntegrationTest.mesInvalido…` |
| BUG-08 | — | pagamentos | Comprovante antigo ao reenviar | **corrigido** (apagado após o commit); estados/`@Version`/409 já existiam | `5747513` | — |
| BUG-09 | — | notificações | Sem paginação | **parcial** — lista já limitada às 100 mais recentes; `markRead` do dono (404 a terceiros) já correto | — | — |
| BUG-10 | — | login | UX | já corrigido (ADR 014; front) | — | testes do front |

## Segredos e implantação
- **Rotacionar:** o `JWT_SECRET` do `.env` local apareceu em texto na saída de um teste durante esta
  auditoria (corrigido: o teste não imprime mais valores) — trate como exposto e gere outro
  (`openssl rand -base64 48`). O `.env` que circulou no zip (Prompt 4) também deve ser considerado
  exposto: **rotacione JWT_SECRET, senhas do banco/pgAdmin/SMTP, ADMIN_PASSWORD, VIDEO_API_SECRET e
  CLINICAL_MASTER_KEYS** (esta última com o procedimento de rotação de `ClinicalDataMaintenance`).
- **`ADMIN_PASSWORD` do `.env` local contém `$`**: no Docker Compose ele é interpretado como variável e
  chega corrompido ao container. Use `$$` ou troque a senha.
- **Variáveis novas:** `API_DOCS_ENABLED` (padrão false), `APP_PORT` (compose prod),
  `SPRING_PROFILES_ACTIVE=prod` (já no compose prod). Propriedades: `psiconet.pagination.max-page-size`,
  `psiconet.mail.retry-attempts`, `psiconet.mail.retry-backoff`, `psiconet.password-reset.cleanup-fixed-rate`.
- **Deploy:** antes de recriar o container, copie os arquivos de `/app/uploads` do container atual
  (`docker cp psiconet_app:/app/uploads ./uploads-backup`) — eles passam a viver no volume `uploads_prod`.
  Coloque a API atrás de proxy reverso com TLS e, se possível, `APP_PORT=127.0.0.1:8080`.
- **Desenvolvimento:** para usar o Swagger local, `API_DOCS_ENABLED=true` no `.env`.

## Riscos residuais e recomendações
- Atualizar para Spring Boot 3.5.x (linha com suporte) — não feito aqui para não misturar com as
  correções; requer rodada própria de testes.
- Flyway com baseline do schema de produção (SEC-16); ShedLock se houver mais de uma instância.
- 2FA para admins e psicólogos; WAF/proxy com TLS e limites de requisição na borda; monitoramento e
  alertas (falhas de e-mail, 5xx, picos de 401/429); rotina mensal de `npm audit`/dependências;
  teste de intrusão externo; exercício do procedimento de incidentes (ADR 015).
- Histórico do Git ainda contém os comprovantes e fotos removidos em `c5e588d` — limpeza exige
  reescrever o histórico (decisão humana; coordenar com quem tem clones).

## Dependem de decisão humana/jurídica
Consentimento de dados de saúde do **psicólogo**: o front passou a pedi-lo, mas `PsychologistRegisterRequest`
não tem o campo e `ConsentServiceImpl` só grava/exige `HEALTH_DATA` para paciente — alinhar exige inventário,
Política de Privacidade e nova versão (ADR 011/013).
BUG-07a (aviso de conflito ao paciente?), BUG-07e (`NO_SHOW` e efeito na cobrança), estorno de
cobrança aprovada em consulta cancelada, reescrita do histórico do Git, prazo de retenção de
comprovantes rejeitados (hoje apagados no reenvio).

## Não alterado (e por quê)
- `ddl-auto=update` em prod (sem dump para baseline do Flyway); Spring Boot 3.3 → 3.5 (escopo).
- Paginação de `GET /notifications` (mudaria o contrato do front; já limitada a 100).
- Conflito de agenda do paciente (privacidade — ADR 017).
