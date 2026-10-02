# Psiconet Backend — Guia para agentes e desenvolvedores

> Fonte oficial de verdade do backend. Substitui o antigo `GEMINI.md` (removido).
> Projeto irmão: **Psiconet-Frontend** (Next.js) — ver `../Psiconet-Frontend/CLAUDE.md`.

## Produto
Psiconet conecta **pacientes** a **psicólogos**: busca de profissionais, conexão (vínculo),
agenda com recorrência, cobranças via Pix com comprovante, notificações e prontuário. Há uma
área **administrativa** para gestão da plataforma (usuários, chamados de suporte, comunicados).

## Stack e versões
- Java 17 (compila com JDK 17+; o build local foi validado com JDK 25), Spring Boot 3.3.5
- Spring Security + JWT stateless (`com.auth0:java-jwt` 4.4.0, HMAC256)
- Spring Data JPA / Hibernate, PostgreSQL 15 (H2 em memória nos testes)
- MapStruct 1.5.5 + Lombok 1.18.42, Bean Validation, SpringDoc OpenAPI 2.6
- Spring Mail (SMTP) · `spring-dotenv` (lê o `.env` da raiz)
- Docker / Docker Compose (Postgres + pgAdmin; app em `docker-compose.prod.yml`)

## Comandos
```bash
./mvnw spring-boot:run          # sobe a API em :8080 (lê .env)
./mvnw test                     # testes (H2, não toca no banco de dev)
./mvnw -DskipTests package      # gera o jar
docker compose up -d            # Postgres (5432) + pgAdmin (5050) para desenvolvimento
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build   # app + banco
```
Se o `mvnw` não tiver permissão de execução: `sh mvnw <goal>`.
Swagger: `http://localhost:8080/swagger-ui/index.html`.

## Estrutura (`src/main/java/com/psiconet`)
```
controllers/        REST (auth, users, patients, psychologists, connections, notifications,
                    admin/, clinical/, financial/)
infra/
  config/           AsyncConfig (pool de e-mail), DataInitializer (admin base)
  exceptions/       BusinessException, EntityNotFoundException, ErrorResponse, GlobalExceptionHandler
  mail/             EmailService(+Impl)
  security/         SecurityConfig, SecurityFilter (JWT), TokenService, AuthorizationService
  storage/          FileStorageService (uploads locais: avatars, receipts)
mapper/             Interfaces MapStruct (Entidade <-> DTO)
model/
  entities/         access/ (User, ChangeLog, PasswordResetCode), profile/, clinical/, financial/,
                    Notification, NotificationPreference
  dtos/             Requests/responses por domínio
  enums/            RoleEnum, UserStatusEnum, NotificationType, clinical/, financial/, conection/
repositories/       Spring Data por domínio
services/
  interfaces/       Contratos
  implement/        Implementações (AuthService é a exceção histórica: classe concreta)
```
Recursos: `src/main/resources/application.properties`. Decisões: `docs/decisoes/`. Diagnóstico
inicial: `docs/diagnostico-fase0.md`.

## Convenções de código
- Camadas: **Controller → Service (interface em `services/interfaces`, impl em `services/implement`) → Repository**.
  Controller não tem regra de negócio; service não conhece HTTP.
- DTOs sempre; **nunca** expor entidade JPA. Conversões com **MapStruct**.
- Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@RequiredArgsConstructor`. Evite `@Data`/`@ToString`
  em entidades JPA (risco de vazar senha/CPF em log e de lazy loading).
- Validação com `@Valid` + Bean Validation, mensagens em **pt-BR**.
- Regras de negócio violadas → `BusinessException(campo, mensagem)` (400). Não encontrado →
  `EntityNotFoundException` (404). Tudo passa pelo `GlobalExceptionHandler` no formato `ErrorResponse`:
  ```json
  { "status": 400, "message": "Mensagem amigável", "timestamp": "...", "errors": { "campo": "detalhe" } }
  ```
- REST: rotas em kebab-case; listagens paginadas (`Page`, serialização `VIA_DTO` → `{ content, page }`);
  `POST` cria (201), `PUT`/`PATCH` atualizam (204), `DELETE` remove (204).
- Endpoints de sucesso que retornam mensagem devolvem **JSON** (`{ "message": "..." }`), nunca texto puro
  — o `apiClient` do frontend faz parse de JSON.
- Usuário autenticado: `@AuthenticationPrincipal User user`. **Nunca** confiar em `userId`/`role` vindos do cliente.
- Performance: `@EntityGraph` em relacionamentos `ToOne`, `@BatchSize` em coleções.
- Commits semânticos (`feat:`, `fix:`, `docs:`, `chore:`, `test:`), mensagens em português.

## Regras de negócio
- **Roles:** `ADMIN`, `PSYCHOLOGIST`, `PATIENT`. Cadastro público só cria `PATIENT` ou `PSYCHOLOGIST`.
- **Status do usuário:** `ACTIVE`, `INACTIVE`, `BLOCKED`, `PENDING_VERIFICATION`. Só `ACTIVE` autentica.
- **Conexões (`Connection`):** status relativo `NONE`, `PENDING_SENT`, `PENDING_RECEIVED`, `CONNECTED`.
  Admin não participa; não é possível conectar-se a si mesmo; só o destinatário aceita/rejeita.
  DTOs de perfil/busca trazem `connectionStatus` e `connectionId`.
- **Vínculo de tratamento (`TreatmentLink`, tabela `paciente_piscologo`):** criado/reativado quando a
  conexão é aceita, desativado quando é removida. Carrega `defaultPrice`.
- **Agendamentos:** só o psicólogo cria; recorrência via `RecurrenceRule`; cancelamento com escopo.
- **Pagamentos:** `PENDING → AWAITING_REVIEW → APPROVED | REJECTED → DISPUTED → CANCELLED`.

## Segurança e LGPD
- JWT no header `Authorization: Bearer`, subject = e-mail, claim `role`.
- `/auth/**` público; `/admin/**` exige `ROLE_ADMIN`; demais rotas autenticadas.
- Senhas com BCrypt. Token de redefinição: 256 bits, só o hash SHA-256 é salvo, uso único.
- CPF, senha e dados clínicos **nunca** em respostas públicas, logs ou mensagens de erro.
- Segredos só por variável de ambiente (`.env` fora do git; `.env.example` sem valores reais).

## Padrão de e-mail e notificação
- E-mails assíncronos (`mailTaskExecutor`); falha de envio só gera log, nunca derruba a operação.
- Notificações in-app via `NotificationService.create(...)`, filtradas pelas preferências do usuário.

## Integração com o frontend
- Frontend: Next.js em `http://localhost:3000` (`FRONTEND_URL`), consome esta API em
  `http://localhost:8080` (`NEXT_PUBLIC_API_URL` no front) **a partir do servidor** (Server Actions).
- Contrato de erro: `ErrorResponse` acima; o front exibe `message` e mapeia `errors` por campo.

## Nunca faça
- Versionar `.env`, segredos ou fallbacks de segredo no `application.properties`.
- Mudança destrutiva de schema (drop/rename de coluna com dados) sem migração e backup.
- Aceitar `role`, `status` ou ids de dono vindos do cliente para decidir permissão.
- Retornar entidade JPA ou CPF completo em resposta.
- Colocar conteúdo clínico em e-mail, notificação, log ou mensagem de erro.
- Rodar testes apontando para o banco de desenvolvimento.
