# ADR 006 — Área administrativa e criação de administradores

- **Status:** aceito
- **Data:** 2026-10-01

## Contexto
Havia listagem/edição básica de usuários sem proteções, sem auditoria, sem criação de administradores
e com máscara de CPF que expunha 5 dígitos. O admin inicial tinha senha padrão hardcoded.

## Decisão
**Acesso**
- Mesma tela de login; o frontend direciona por role para `/admin/*`.
- Autorização em camadas: rota `/admin/**` com `hasRole('ADMIN')` no `SecurityConfig` **e**
  `@PreAuthorize` nos controllers; no frontend, `proxy.ts` e `admin/layout.tsx` (só reflexo de UI).
  Teste parametrizado garante 403 para PATIENT e PSYCHOLOGIST (e 401 sem token) em **todos** os
  endpoints admin.

**Administradores**
- **Admin base**: criado de forma idempotente pelo `DataInitializer` a partir de `ADMIN_EMAIL` /
  `ADMIN_PASSWORD` (sem valores padrão), com troca de senha obrigatória. Em bases existentes, o admin
  com `ADMIN_EMAIL` (ou o mais antigo) é marcado como base. Flag `usuario.admin_base`.
- **Somente admins criam admins** (`POST /admin/admins`), sempre **conta nova** (e-mail e CPF
  exclusivos), `criado_por_id` = admin que criou (a cadeia remonta ao admin base).
- **Não existe caminho para promover paciente/psicólogo**: nenhum DTO admin possui `role`; o cadastro
  público não aceita `role`; criar admin com e-mail de conta existente → 409.
- Senha temporária definida por quem cria e repassada fora da plataforma; **não vai por e-mail**. O
  e-mail de convite só avisa e leva ao login; a troca é obrigatória no 1º acesso.
- Proteções: não alterar o próprio status; admin base não pode ser inativado/bloqueado; sempre resta
  ao menos um admin ativo (checagem com lock pessimista na linha do alvo).

**Usuários**
- Ativar/inativar/bloquear via `PATCH /admin/users/{id}/status`; inativação incrementa a versão do
  token (sessões caem na hora) e o filtro checa status a cada requisição.
- Correção de cadastro (`PUT /admin/users/{id}`): nome, telefone, e-mail (troca de e-mail derruba sessões).
- Lista paginada com busca (nome/e-mail) e filtros (role, status) **no servidor**; colunas nome, role,
  data de criação, situação e **CPF mascarado no backend estilo Pix** (`•••.456.789-••`).
- Admin não vê conteúdo clínico em nenhuma tela/endpoint.

**Auditoria**: `log_alteracao` (entidade `@Immutable`) registra autor, ação, alvo, IP e data de toda ação
administrativa (status, edição, criação de admin, comunicados, chamados). Detalhes nunca incluem
valores pessoais (só nomes de campos alterados).

**Dashboard**: `GET /admin/dashboard` agrega contagens (psicólogos/pacientes ativos, totais, chamados
abertos × resolvidos) — nenhum dado pessoal.

**Comunicados**: `POST /admin/notifications` → notificação obrigatória + e-mail (template
`comunicado`), rate limit `admin-announcement`, auditado.

## Consequências
- O admin base criado por variável de ambiente usa CPF sentinela `00000000000` (coluna NOT NULL/UNIQUE).
- Backlog: 2FA para administradores; tela de consulta da auditoria.
