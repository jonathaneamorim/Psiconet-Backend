# Psiconet Backend — Guia para agentes e desenvolvedores

> Fonte oficial de verdade do backend (substitui o antigo `GEMINI.md`, removido).
> Projeto irmão: **Psiconet-Frontend** (Next.js) — `../Psiconet-Frontend/CLAUDE.md`.
> Decisões registradas em `docs/decisoes/` (ADRs 000–010). Diagnóstico inicial: `docs/diagnostico-fase0.md`.

## Produto
Psiconet conecta **pacientes** a **psicólogos**: busca, conexão (vínculo), agenda com recorrência,
cobranças via Pix, notificações, **prontuário eletrônico cifrado** com anotações de sessão,
**chamados de suporte** e **área administrativa** (usuários, administradores, dashboard, comunicados).

## Stack e versões
- Java 17 (build validado com JDK 25), Spring Boot 3.3.5, Spring Security + JWT (`java-jwt` 4.4.0, HS256)
- Spring Data JPA / Hibernate, PostgreSQL 15 · **H2 em memória nos testes** (perfil `test`)
- MapStruct 1.5.5, Lombok 1.18.42, Bean Validation, SpringDoc 2.6, `spring-dotenv` (lê `.env`)
- Thymeleaf **core** (templates de e-mail), Bucket4j 8.14 (rate limit), OpenPDF 2.0.3 (PDF do prontuário)

## Comandos
```bash
./mvnw spring-boot:run                  # API em :8080 (lê .env; se mvnw sem +x: sh mvnw ...)
./mvnw test                             # 115+ testes, H2 — nunca toca no banco de dev
./mvnw -DskipTests package              # jar em target/
docker compose up -d                    # Postgres (5432) + pgAdmin (5050)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # inclui chamados de exemplo (DevDataSeeder)
```
Swagger: `http://localhost:8080/swagger-ui/index.html`.

## Estrutura (`src/main/java/com/psiconet`)
```
controllers/            REST: AuthController, UserController, NotificationController, ...
  admin/                AdminController (/admin), AdminTicketController (/admin/tickets)
  support/              TicketController (/tickets)
  clinical/             Appointment, RecurrenceRule, TreatmentLink,
                        MedicalRecordController (/medical-records — psicólogo),
                        PatientMedicalRecordController (/patient/medical-records — paciente)
infra/
  config/               DataInitializer (admin base), SchemaPatches (DDL idempotente), DevDataSeeder (perfil dev),
                        ClinicalDataMaintenance (migração legado + rotação de chave), AsyncConfig, TicketProperties
  crypto/               ClinicalCryptoService (AES-256-GCM, envelope), MasterKeyProvider/EnvMasterKeyProvider
  exceptions/           BusinessException(400), ConflictException(409), EntityNotFound(404), AccountStatus(403),
                        TooManyRequests(429), ClinicalUnavailable(503), ErrorResponse, GlobalExceptionHandler
  mail/                 EmailService (fachada), EmailDispatcher (AFTER_COMMIT + @Async), EmailRenderer, EmailTemplate
  ratelimit/            @RateLimited, RateLimitInterceptor, RateLimiterService, RateLimitProperties
  richtext/             RichTextSanitizer (whitelist do JSON do editor)
  security/             SecurityConfig, SecurityFilter (JWT + status + versão), TokenService, JsonSecurityErrorHandlers
  text/                 TextSanitizer, CpfMasker
  web/                  ClientIpResolver, WebMvcConfig, ClinicalNoStoreFilter
mapper/ model/{entities,dtos,enums}/ repositories/ services/{interfaces,implement}/
src/main/resources/templates/email/   layout/, components/, <template>.html, text/<template>.txt
```

## Convenções de código
- Camadas **Controller → Service (interface + impl) → Repository**; DTOs sempre; MapStruct; sem `@Data`/`@ToString` em entidades.
- `@AuthenticationPrincipal User user`; **nunca** confiar em `role`/`userId`/`status` vindos do cliente.
- Erros no formato `ErrorResponse { status, code?, message, timestamp, errors? }`. `code` estável para o front:
  `UNAUTHENTICATED`, `FORBIDDEN`, `PASSWORD_CHANGE_REQUIRED`, `RATE_LIMITED`, `ACCOUNT_INACTIVE|BLOCKED|PENDING`, `CLINICAL_UNAVAILABLE`.
- Sucesso com mensagem sempre em JSON (`MessageResponseDTO`) — nunca texto puro (causa do bug do cadastro, ADR 002).
- Recurso de outro usuário → **404** (anti-IDOR); permissão de papel → 403 via `@PreAuthorize`.
- Textos livres passam por `TextSanitizer`; mensagens e e-mails em **pt-BR**.
- Schema: `ddl-auto=update` só aditivo; ajustes que o Hibernate não faz vão em `SchemaPatches` (idempotentes,
  não destrutivos, só Postgres). Recomendação: Flyway antes de produção (ADR 001).
- Commits semânticos em português; testes obrigatórios para regra crítica.

## Regras de negócio
- **Roles** `ADMIN`, `PSYCHOLOGIST`, `PATIENT`. Cadastro público só cria paciente/psicólogo (DTOs sem `role`).
- **Status** `ACTIVE`, `INACTIVE`, `BLOCKED`, `PENDING_VERIFICATION`; só `ACTIVE` autentica; checado a cada requisição.
- **Sessão**: JWT com claim `ver` = `usuario.versao_token`; logout, troca/redefinição de senha, inativação e troca de
  e-mail incrementam → tokens antigos morrem. "Manter login" = token de 168 h. Troca de senha obrigatória bloqueia
  tudo exceto `/auth/change-password`, `/auth/logout`, `/users/me`.
- **Login**: senha conferida antes do status (sem enumeração); mensagens próprias por status.
- **Admin base** (`admin_base`): criado/marcado de forma idempotente pelo `DataInitializer` (`ADMIN_EMAIL`/`ADMIN_PASSWORD`,
  troca obrigatória). **Só admins criam admins** (`POST /admin/admins`, conta nova, `criado_por_id`); paciente/psicólogo
  **nunca** viram admin. Admin não altera o próprio status; admin base não pode ser inativado; sempre ≥ 1 admin ativo.
- **CPF** mascarado no backend estilo Pix (`•••.456.789-••`, `CpfMasker`). Admin nunca vê conteúdo clínico.
- **Conexões/vínculo**: aceitar conexão cria/reativa `TreatmentLink`; remover desativa.
- **Chamados**: `OPEN → IN_PROGRESS → RESOLVED → CLOSED` (resposta do usuário reabre `RESOLVED`; `CLOSED` é terminal).
  Máx. 3 ativos por usuário, 2 min entre aberturas, duplicatas por hash (24 h / 10 min). Resposta ou finalização do admin
  ⇒ notificação + e-mail (só protocolo). Admin aparece ao usuário como "Equipe Psiconet".
- **Prontuário** (ADRs 008–010): um por vínculo; só o psicólogo titular lê/edita (vínculo encerrado: leitura + adendos);
  anotações `RECORD` (documental) × `PRIVATE` (pessoal); rascunho → finalizado imutável com hash SHA-256 + nome/CRP;
  correções por adendo encadeado. Psicólogo exporta PDF de guarda (`/medical-records/links/{id}/pdf`, opcional
  `includePrivateNotes`). Editor aceita alinhamento (center/right) e tamanho de fonte da lista fixa. Paciente **solicita** acesso; psicólogo libera (somente leitura, prazo/PDF opcionais)
  ou nega com justificativa. Tudo em `auditoria_clinica` (hash encadeado, imutável).

## Padrão de e-mail (ADR 003)
- Novo e-mail = item em `EmailTemplate` + `templates/email/<nome>.html` (usa `layout/base` e `components/*`) +
  `templates/email/text/<nome>.txt` + método em `EmailService`. Conteúdo dinâmico **sempre** `th:text` (escapado).
- Envio via evento: só após o commit, assíncrono (`mailTaskExecutor`), falha = log mascarado. Sem `MAIL_FROM`, desabilitado.
- **Nenhum e-mail contém conteúdo clínico**, texto de chamado, motivo de negativa ou nome de paciente para psicólogo.

## Padrão de notificação (ADR 005)
- `NotificationService.create(recipient, actor, type, title, message, referenceType, referenceId)`.
- `referenceType`: `APPOINTMENT`, `CONNECTION`, `PAYMENT`, `TICKET`, `ANNOUNCEMENT`, `MEDICAL_RECORD` (o front roteia por ele).
- Tipos obrigatórios (`NotificationType.isMandatory()`): chamados, comunicados e prontuário — ignoram preferências.
- Mensagens nunca contêm conteúdo clínico.

## Rate limit (ADR 004)
- `@RateLimited("politica")` no endpoint; limites **só** em `psiconet.rate-limit.policies.*` (application.properties).
- Conta por IP (X-Forwarded-For aceito apenas de `TRUSTED_PROXIES`) e por usuário; 429 + `Retry-After`.
- Políticas: `login`, `login-account`, `register`, `forgot-password`, `reset-password`, `change-password`, `ticket-open`,
  `ticket-message`, `record-access-request`, `admin-announcement`, `admin-user-write`, `clinical-write`.

## Segurança e LGPD
- Segredos só por ambiente (`JWT_SECRET` ≥ 32 chars obrigatório; `CLINICAL_MASTER_KEYS`; `ADMIN_*`; SMTP).
- Dados clínicos: AES-256-GCM por campo, DEK por prontuário, KEK versionada fora do banco; sem chave → 503.
  Respostas clínicas com `Cache-Control: no-store`. Triggers no Postgres bloqueiam DELETE em prontuário/anotações e
  UPDATE/DELETE em auditorias/adendos/anotação finalizada.
- Auditoria administrativa em `log_alteracao` (imutável); auditoria clínica em `auditoria_clinica` (hash encadeado).
- Logs: nunca senha, token, CPF completo, e-mail completo em erro de envio, conteúdo clínico ou valores de constraint.

## Integração com o frontend
- Front em `http://localhost:3000` (`FRONTEND_URL`), chama a API **pelo servidor** (Server Actions) e repassa o IP do
  navegador em `X-Forwarded-For`. Base da API no front: `NEXT_PUBLIC_API_URL` (padrão `http://localhost:8080`).
- Paginação `{ content, page: { number, size, totalElements, totalPages } }` (VIA_DTO).
- PDF do prontuário: `GET /patient/medical-records/access-requests/{id}/pdf` (cabeçalho `X-Document-SHA256`), servido ao
  navegador pela rota `/api/medical-records/[requestId]/pdf` do front.

## Nunca faça
- Versionar `.env`, segredos ou fallback de segredo no `application.properties`.
- Mudança destrutiva de schema sem migração e backup; apagar dado clínico; sobrescrever anotação finalizada.
- Aceitar `role`/`status`/ids de dono do cliente para decidir permissão; criar caminho de promoção a ADMIN.
- Expor entidade JPA, CPF completo ou conteúdo clínico a admin, em e-mail, notificação, log ou mensagem de erro.
- Aceitar HTML do cliente no conteúdo clínico (só JSON do editor, validado pelo `RichTextSanitizer`).
- Rodar testes contra o banco de desenvolvimento; usar `th:utext` com dado de usuário.
