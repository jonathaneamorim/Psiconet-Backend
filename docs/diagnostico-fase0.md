# Diagnóstico inicial — Psiconet (Backend + Frontend)

> Varredura feita em 01/10/2026, antes da evolução descrita no plano em fases.
> Escopo: `Psiconet-Backend` (Spring Boot) e `Psiconet-Frontend` (Next.js). Pastas de artefatos
> (`.claude/worktrees`, `node_modules`, `.next`, `target`, `uploads`, `backups`) foram ignoradas.

## 1. Estado de partida

| Item | Backend | Frontend |
|---|---|---|
| Build | ✅ `./mvnw compile` | ✅ `next build` |
| Testes | ✅ 8 testes (1 de contexto + 7 de recorrência) — **o teste de contexto usava o banco de desenvolvimento real** | ⚠️ 20/21 (teste do `LateralMenu` desatualizado) |
| Lint | — | ❌ `npm run lint` quebrava (ESLint 10 incompatível com `eslint-config-next` 16) |
| Gestão de schema | `spring.jpa.hibernate.ddl-auto=update` (sem Flyway/Liquibase) | — |
| Dados no banco de dev | 6 usuários, 0 prontuários, 0 anotações de sessão, 0 registros em `log_alteracao` | — |

## 2. Requisitos × o que existe

| Requisito | Existe | Falta / problema encontrado |
|---|---|---|
| **Cadastro** | `AuthController`/`AuthService` transacionais; `FormRegister` com loading | **Causa raiz do bug:** o backend responde `201` com corpo *texto puro* ("Paciente cadastrado com sucesso!"); o `apiClient` faz `JSON.parse` em qualquer corpo de sucesso, lança exceção e devolve "Erro de conexão com o servidor." — a conta já foi criada, e o segundo clique recebe "E-mail já cadastrado." Também: erros por campo do backend (`errors`) eram descartados pela action; `System.out.println` no controller; CPF duplicado revela existência de CPF. |
| **Login** | JWT HS256, cookie httpOnly | Status da conta (inativa/bloqueada) era checado **antes** da senha → revela que o e-mail existe; `BLOCKED` caía em mensagem genérica; token continuava válido após inativação; logout só apagava o cookie. |
| Segredos | `.env` fora do git | `application.properties` versiona **fallbacks reais** (JWT secret, senha do banco, senha do admin); `.env.example` contém um JWT secret; a variável `APP_JWT_SECRET` do `.env` não era lida (o código lê `JWT_SECRET`) — o segredo efetivo era o hardcoded. **Recomenda-se rotacionar JWT secret e senha do banco.** |
| E-mails | `EmailServiceImpl` assíncrono, só para redefinição de senha, HTML inline em Java | Sem diretório de templates, sem layout reutilizável, sem versão texto, envio disparado dentro da transação. |
| Notificações | `Notification`, `NotificationService`, preferências por categoria, sino com polling | Sem tipos para chamados/comunicados/prontuário; sem conceito de notificação obrigatória. |
| Rate limit | — | Inexistente. |
| Admin | `AdminController` (`/admin/users`, status, edição), `UsersTable`, `/admin/users` | Sem busca/filtros no servidor; máscara de CPF expõe 5 dígitos (`123.***.***-45`); sem criação de admins; sem proteções (auto-inativação, último admin); sem auditoria; `/admin/dashboard` vazio; `admin/layout.tsx` sem guarda; status do front (`PENDING`/`SUSPENDED`) não batem com o enum do backend (`PENDING_VERIFICATION`/`BLOCKED`). |
| Admin base | `DataInitializer` cria admin se nenhum existir | Credenciais com fallback hardcoded (`psiconet123`), sem troca obrigatória de senha, sem marcação de "admin base". |
| Auditoria | Entidade `ChangeLog` (`log_alteracao`) sem uso | Faltam campos (alvo, IP, detalhes). |
| Chamados | — | Inexistente (entidades, API, telas, notificações). |
| Prontuário | `MedicalRecord` (texto puro) e `SessionNote` (texto puro), sem service/controller | Sem criptografia, sem controle de acesso, sem auditoria, sem editor, sem integridade/imutabilidade, sem fluxo de acesso do paciente. |
| Menu do usuário | Dropdown no `Header` com "Meu Perfil", "Configurações", "Sair" | Sem "Suporte". |
| CORS | `allowedOrigins("*")` | Aceitável porque o front chama a API pelo servidor (Server Actions), mas o ideal é restringir à URL do frontend. |

## 3. Riscos e dívidas técnicas registradas

1. Segredos versionados (ver acima) — mitigado nesta entrega removendo os fallbacks; **rotação fica para humanos**.
2. Testes de integração rodavam contra o banco de desenvolvimento com `ddl-auto=update` — passam a usar H2 em memória.
3. `User` tinha `@ToString` incluindo o hash da senha.
4. 19 erros de lint pré-existentes no frontend (principalmente `react-hooks/set-state-in-effect`) — não bloqueiam build; código novo deve sair sem erros de lint.
5. `notificationApiClient.ts` e `ConnectionRequestsDropdown.tsx` sem uso (já documentado no CLAUDE.md do front).
