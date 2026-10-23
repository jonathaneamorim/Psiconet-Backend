# Psiconet Backend — Guia para agentes e desenvolvedores

> Fonte oficial de verdade do backend (substitui o antigo `GEMINI.md`, removido).
> Projeto irmão: **Psiconet-Frontend** (Next.js) — `../Psiconet-Frontend/CLAUDE.md`.
> Decisões registradas em `docs/decisoes/` (ADRs 000–016). Diagnóstico inicial: `docs/diagnostico-fase0.md`.
> Documentos legais e inventário de dados: `docs/legal/`.

## Produto
Psiconet conecta **pacientes** a **psicólogos**: busca, conexão (vínculo), agenda com recorrência,
cobranças via Pix, notificações, **sessões on-line na Sala Psiconet (vídeo integrado, sem gravação)**,
**prontuário eletrônico cifrado** com anotações de sessão,
**chamados de suporte** e **área administrativa** (usuários, administradores, dashboard, comunicados).

## Stack e versões
- Java 17 (build validado com JDK 25), Spring Boot 3.3.5, Spring Security + JWT (`java-jwt` 4.4.0, HS256)
- Spring Data JPA / Hibernate, PostgreSQL 15 · **H2 em memória nos testes** (perfil `test`)
- MapStruct 1.5.5, Lombok 1.18.42, Bean Validation, SpringDoc 2.6, `spring-dotenv` (lê `.env`)
- Thymeleaf **core** (templates de e-mail), Bucket4j 8.14 (rate limit), OpenPDF 2.0.3 (PDF do prontuário)

## Comandos
```bash
./mvnw spring-boot:run                  # API em :8080 (lê .env; se mvnw sem +x: sh mvnw ...)
./mvnw test                             # 150+ testes, H2 — nunca toca no banco de dev
./mvnw -DskipTests package              # jar em target/
docker compose up -d                    # Postgres (5432) + pgAdmin (5050)
docker compose --profile video up -d livekit   # LiveKit local (:7880) p/ testar a Sala Psiconet (VIDEO_* no .env)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # inclui chamados de exemplo (DevDataSeeder)
```
Swagger: `http://localhost:8080/swagger-ui/index.html`.

## Estrutura (`src/main/java/com/psiconet`)
```
controllers/            REST: AuthController, UserController, NotificationController, ...
  admin/                AdminController (/admin), AdminTicketController (/admin/tickets)
  support/              TicketController (/tickets)
  legal/                LegalController (/legal: versions, documents/{slug}, consents)
  video/                VideoController (GET /video/config, POST /webhooks/video)
  clinical/             Appointment, RecurrenceRule, TreatmentLink,
                        MedicalRecordController (/medical-records — psicólogo),
                        PatientMedicalRecordController (/patient/medical-records — paciente)
infra/
  config/               DataInitializer (admin base), SchemaPatches (DDL idempotente), DevDataSeeder (perfil dev),
                        ClinicalDataMaintenance (migração legado + rotação de chave), AsyncConfig, TicketProperties
  crypto/               ClinicalCryptoService (AES-256-GCM, envelope), MasterKeyProvider/EnvMasterKeyProvider
  exceptions/           BusinessException(400), ConflictException(409), EntityNotFound(404), AccountStatus(403),
                        TooManyRequests(429), ClinicalUnavailable(503), LegalVersionOutdated(409), ErrorResponse,
                        GlobalExceptionHandler
  legal/                LegalDocumentRegistry (lê docs/legal empacotados em classpath:legal/), LegalDocumentType
  mail/                 EmailService (fachada), EmailDispatcher (AFTER_COMMIT + @Async), EmailRenderer, EmailTemplate
  ratelimit/            @RateLimited, RateLimitInterceptor, RateLimiterService, RateLimitProperties
  richtext/             RichTextSanitizer (whitelist do JSON do editor)
  security/             SecurityConfig (+ cabeçalhos), SecurityFilter (JWT + status + versão + sessão + aceite),
                        TokenService (acesso 15 min, claim sid), SessionService (refresh rotativo), SessionProperties,
                        JsonSecurityErrorHandlers
  text/                 TextSanitizer, CpfMasker
  video/                VideoProvider (abstração), VideoProperties (psiconet.video.*), livekit/LiveKitVideoProvider,
                        VideoAccessException (códigos VIDEO_*), InvalidWebhookSignatureException
  web/                  ClientIpResolver (+ X-Client-User-Agent), UserAgentSummarizer, WebMvcConfig, ClinicalNoStoreFilter,
                        RequestSizeLimitFilter (corpo ≤ 2 MB sem arquivo; webhook de vídeo ≤ 64 KB ⇒ 413)
mapper/ model/{entities,dtos,enums}/ repositories/ services/{interfaces,implement}/
src/main/resources/templates/email/   layout/, components/, <template>.html, text/<template>.txt
docs/legal/                           termos-de-uso.md, politica-de-privacidade.md, politica-de-cookies.md (fonte única),
                                      inventario-de-dados.md
```

## Convenções de código
- Camadas **Controller → Service (interface + impl) → Repository**; DTOs sempre; MapStruct; sem `@Data`/`@ToString` em entidades.
- `@AuthenticationPrincipal User user`; **nunca** confiar em `role`/`userId`/`status` vindos do cliente.
- Erros no formato `ErrorResponse { status, code?, message, timestamp, errors? }`. `code` estável para o front:
  `UNAUTHENTICATED`, `FORBIDDEN`, `PASSWORD_CHANGE_REQUIRED`, `CONSENT_REQUIRED`, `LEGAL_VERSION_OUTDATED`,
  `SESSION_EXPIRED`, `RATE_LIMITED`, `PAYLOAD_TOO_LARGE`, `ACCOUNT_INACTIVE|BLOCKED|PENDING`, `CLINICAL_UNAVAILABLE`,
  `VIDEO_NOT_IN_APP`, `VIDEO_APPOINTMENT_INACTIVE`, `VIDEO_WINDOW_NOT_OPEN`, `VIDEO_WINDOW_CLOSED`, `VIDEO_UNAVAILABLE`.
- Sucesso com mensagem sempre em JSON (`MessageResponseDTO`) — nunca texto puro (causa do bug do cadastro, ADR 002).
- Recurso de outro usuário → **404** (anti-IDOR); permissão de papel → 403 via `@PreAuthorize`.
- Textos livres passam por `TextSanitizer`; mensagens e e-mails em **pt-BR**.
- Schema: `ddl-auto=update` só aditivo; ajustes que o Hibernate não faz vão em `SchemaPatches` (idempotentes,
  não destrutivos, só Postgres). Recomendação: Flyway antes de produção (ADR 001).
- Commits semânticos em português; testes obrigatórios para regra crítica.

## Regras de negócio
- **Roles** `ADMIN`, `PSYCHOLOGIST`, `PATIENT`. Cadastro público só cria paciente/psicólogo (DTOs sem `role`),
  exige **18+** e aceite dos documentos vigentes (ADR 013).
- **Status** `ACTIVE`, `INACTIVE`, `BLOCKED`, `PENDING_VERIFICATION`; só `ACTIVE` autentica; checado a cada requisição.
- **Sessão** (ADR 014): token de acesso JWT de **15 min** (claims `ver` = `usuario.versao_token` e `sid` = sessão) +
  **refresh rotativo** por dispositivo em `sessao_usuario` (só hash; reutilização revoga a sessão). Sem "manter
  conectado": 2 h sem uso / 12 h total; com: 7 d sem uso / 30 d total — durações **só** em `psiconet.session.*`.
  `POST /auth/refresh`, `/auth/logout` (este dispositivo), `/auth/logout-all` (todos). Troca/redefinição de senha,
  inativação e troca de e-mail derrubam as sessões. Troca de senha obrigatória bloqueia tudo exceto
  `/auth/change-password`, `/auth/logout`, `/users/me`.
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

## Videochamada — Sala Psiconet (ADR 016)
- **Arquitetura:** provedor SFU/WebRTC gerenciado (**LiveKit** Cloud ou self-host) atrás de `VideoProvider`. O backend só
  cria/garante a sala, emite **token curto por participante** e valida webhooks; a mídia vai do navegador ao provedor.
  Adaptador próprio (JWT HS256 com `java-jwt` + Twirp via `RestClient`) — o SDK Java conflita com o BOM do Boot 3.3.
- **Agendamento:** `meetingProvider` em `AppointmentCreateDTO`/`RecurrenceRuleCreateDTO` (`IN_APP` | `EXTERNAL_LINK`;
  omitido = `EXTERNAL_LINK`). Regras em `MeetingFieldsValidator` (link externo só http/https ≤ 255, sem esquema perigoso;
  `IN_APP` ignora link e exige a flag). `IN_APP` gera `roomId` (128 bits `SecureRandom`) na criação e **em cada ocorrência**
  materializada pela recorrência. `roomId` **nunca** sai em DTO; `AppointmentDTO` expõe `joinOpensAt`/`joinClosesAt`.
- **Entrada:** `GET /appointments/{id}` e `POST /appointments/{id}/video/join` (rate limit `video-join`): só psicólogo/paciente
  do vínculo (senão 404), `VIDEO_CALL + IN_APP`, `ACCEPTED` ou `COMPLETED` na tolerância, vínculo ativo, dentro da janela
  (`VIDEO_JOIN_OPENS_BEFORE` 30 min antes do início → `VIDEO_JOIN_CLOSES_AFTER` 15 min após o fim). Token: só
  câmera/microfone, sem dados/tela/gravação/admin, identidade = id do usuário, sem nome/metadados, sala com **máx. 2**.
  Psicólogo = anfitrião (`POST .../video/end` encerra para todos); paciente aguarda na sala até ele entrar.
- **Ciclo da sala:** nasce na entrada (`CreateRoom` idempotente); todos saíram ⇒ fecha em 5 s
  (`departure-timeout`); nova entrada reabre. **Sozinho 15 min** (`VIDEO_ALONE_TIMEOUT`) ⇒ o front avisa e
  desconecta; `VideoRoomWatchdog` (1 min) remove pelo provedor quem passar do limite + 1 min.
  **Limite de horário** `VIDEO_HARD_STOP_AFTER_END` (fim + 60 min, `hardStopAt` na credencial): o front avisa e
  sai; o job encerra a sala para todos 1 min depois. Webhook com teto de corpo de 64 KB (413).
- **Webhook** `POST /webhooks/video` (permitAll + assinatura obrigatória): presença em `log_sessao_video` (imutável,
  idempotente). **Só registra** — não muda status nem pagamento. Primeira entrada do psicólogo ⇒ `VIDEO_PARTICIPANT_JOINED`.
- **Lembrete:** `VideoSessionReminderService` (1 min) ⇒ `VIDEO_SESSION_REMINDER` + e-mail `lembrete-sessao-video` ~15 min
  antes, preferência "agendamento", marcação atômica `lembrete_video_enviado_em`.
- **Desligar:** `VIDEO_ENABLED=false` (padrão) ou sem `VIDEO_URL`/`VIDEO_API_KEY`/`VIDEO_API_SECRET` ⇒ `GET /video/config`
  = `false`, `IN_APP` recusado na criação, entrada 503, sem lembretes; link externo segue funcionando.
- **Não fazemos:** gravação, transcrição ou armazenamento de mídia; chat; compartilhamento de tela. Fuso: `LocalDateTime`
  do servidor — produção em `America/Sao_Paulo`.

## Padrão de e-mail (ADR 003)
- Novo e-mail = item em `EmailTemplate` + `templates/email/<nome>.html` (usa `layout/base` e `components/*`) +
  `templates/email/text/<nome>.txt` + método em `EmailService`. Conteúdo dinâmico **sempre** `th:text` (escapado).
- Envio via evento: só após o commit, assíncrono (`mailTaskExecutor`), falha = log mascarado. Sem `MAIL_FROM`, desabilitado.
- **Nenhum e-mail contém conteúdo clínico**, texto de chamado, motivo de negativa ou nome de paciente para psicólogo.

## Padrão de notificação (ADR 005)
- `NotificationService.create(recipient, actor, type, title, message, referenceType, referenceId)`.
- Vídeo: `VIDEO_SESSION_REMINDER`, `VIDEO_PARTICIPANT_JOINED` (categoria "agendamento"; `referenceType = APPOINTMENT`).
- `referenceType`: `APPOINTMENT`, `CONNECTION`, `PAYMENT`, `TICKET`, `ANNOUNCEMENT`, `MEDICAL_RECORD`, `LEGAL` (o front roteia por ele).
- Tipos obrigatórios (`NotificationType.isMandatory()`): chamados, comunicados, prontuário e `LEGAL_DOCUMENTS_UPDATED` — ignoram preferências.
- Mensagens nunca contêm conteúdo clínico.

## Rate limit (ADR 004)
- `@RateLimited("politica")` no endpoint; limites **só** em `psiconet.rate-limit.policies.*` (application.properties).
- Conta por IP (X-Forwarded-For aceito apenas de `TRUSTED_PROXIES`) e por usuário; 429 + `Retry-After`.
- Políticas: `login`, `login-account`, `register`, `forgot-password`, `reset-password`, `refresh`, `change-password`, `ticket-open`,
  `ticket-message`, `record-access-request`, `admin-announcement`, `admin-user-write`, `clinical-write`, `video-join`,
  `video-webhook`.

## Legal e Privacidade (ADRs 011–015)
- **Documentos** (minutas, com `[A PREENCHER]`): `docs/legal/*.md` com cabeçalho `tipo/titulo/versao/vigencia` e
  "Histórico de versões"; empacotados no jar; a API não sobe se faltar algum. `GET /legal/versions` e
  `GET /legal/documents/{terms|privacy|cookies}` são públicos. **Nova versão:** atualizar o inventário, o documento
  (versão + vigência + histórico) e `TestData.LEGAL_VERSION`; no deploy, `LegalVersionPublisher` avisa os usuários
  ativos (notificação + e-mail) e o `SecurityFilter` passa a exigir o reaceite (403 `CONSENT_REQUIRED`, exceto
  `/legal/**`, `/users/me`, `/auth/logout*`, `/auth/change-password`).
- **Prova de aceite:** `consentimento_usuario` (TERMS, PRIVACY e, para paciente, HEALTH_DATA; versão, data/IP/navegador
  resumido **do servidor**, origem) — só inserção (`@Immutable` + trigger). Gravada na transação do cadastro.
- **Inventário** (`docs/legal/inventario-de-dados.md`): atualize ao coletar dado novo, criar cookie ou integrar
  terceiro — antes de mexer nos documentos.
- Canal do titular: chamado categoria `PRIVACY` ("Privacidade / LGPD"). Eliminação de conta: manual por enquanto
  (prontuário tem guarda obrigatória). Procedimento de incidentes: ADR 015.

## Segurança e LGPD
- Segredos só por ambiente (`JWT_SECRET` ≥ 32 chars obrigatório; `CLINICAL_MASTER_KEYS`; `ADMIN_*`; SMTP; `VIDEO_API_SECRET`).
- Dados clínicos: AES-256-GCM por campo, DEK por prontuário, KEK versionada fora do banco; sem chave → 503.
  Respostas clínicas com `Cache-Control: no-store`. Triggers no Postgres bloqueiam DELETE em prontuário/anotações e
  UPDATE/DELETE em auditorias/adendos/anotação finalizada.
- Auditoria administrativa em `log_alteracao` (imutável); auditoria clínica em `auditoria_clinica` (hash encadeado).
- Logs: nunca senha, token, CPF completo, e-mail completo em erro de envio, conteúdo clínico ou valores de constraint.
- Cabeçalhos: CSP `default-src 'none'` (exceto Swagger), `Referrer-Policy: no-referrer`, `Permissions-Policy` (ADR 015).

## Integração com o frontend
- Front em `http://localhost:3000` (`FRONTEND_URL`), chama a API **pelo servidor** (Server Actions) e repassa o IP do
  navegador em `X-Forwarded-For` e o navegador em `X-Client-User-Agent` (ambos aceitos só de `TRUSTED_PROXIES`). Base da API no front: `NEXT_PUBLIC_API_URL` (padrão `http://localhost:8080`).
- Paginação `{ content, page: { number, size, totalElements, totalPages } }` (VIA_DTO).
- PDF do prontuário: `GET /patient/medical-records/access-requests/{id}/pdf` (cabeçalho `X-Document-SHA256`), servido ao
  navegador pela rota `/api/medical-records/[requestId]/pdf` do front.

## Nunca faça
- Gravar, transcrever ou armazenar áudio/vídeo das sessões; expor `roomId` ou logar tokens de vídeo; dar ao token de
  participante permissões além de câmera/microfone; enviar nome, e-mail ou dado clínico ao provedor de vídeo.
- Versionar `.env`, segredos ou fallback de segredo no `application.properties`.
- Mudança destrutiva de schema sem migração e backup; apagar dado clínico; sobrescrever anotação finalizada.
- Aceitar `role`/`status`/ids de dono do cliente para decidir permissão; criar caminho de promoção a ADMIN.
- Expor entidade JPA, CPF completo ou conteúdo clínico a admin, em e-mail, notificação, log ou mensagem de erro.
- Aceitar HTML do cliente no conteúdo clínico (só JSON do editor, validado pelo `RichTextSanitizer`).
- Rodar testes contra o banco de desenvolvimento; usar `th:utext` com dado de usuário.
- Alterar documento legal sem subir a versão/histórico; aceitar data, IP ou versão de aceite vindos do cliente;
  apagar/alterar registro de `consentimento_usuario`; guardar refresh token em texto claro.
