# ADR 002 — Correção do cadastro, login seguro e invalidação de sessão

- **Status:** aceito
- **Data:** 2026-10-01

## Contexto (bug relatado)
"Ao criar a conta nada acontece; ao tentar de novo, diz que a conta já existe."

**Causa raiz confirmada:** `POST /auth/register/*` respondia `201` com corpo **texto puro**. O
`apiClient` do frontend faz `JSON.parse` de todo corpo de sucesso; a exceção caía no `catch` genérico
e virava "Erro de conexão com o servidor." — com a conta **já criada**. A segunda tentativa recebia,
corretamente, "E-mail já cadastrado". Agravantes: erros por campo do backend eram descartados pela
server action; o botão voltava a ficar habilitado logo após o sucesso.

Outros problemas no login: o status da conta era verificado **antes** da senha (o Spring checa
`isEnabled()` primeiro), revelando a existência do e-mail; `BLOCKED` não tinha mensagem própria;
tokens continuavam válidos após inativação; logout só apagava o cookie; o segredo JWT efetivo era um
fallback versionado.

## Decisão
- **Toda resposta de sucesso com mensagem é JSON** (`MessageResponseDTO`, `RegisterResponseDTO`), e o
  `apiClient` passou a tolerar corpos não-JSON (defesa dupla).
- **Cadastro atômico:** usuário + perfil na mesma transação com `saveAndFlush`; violação de constraint
  concorrente vira 409 amigável. E-mail de boas-vindas via evento `AFTER_COMMIT` + `@Async`
  (ADR 003): falha de SMTP só gera log.
- **Duplicidade:** e-mail duplicado → 409 com `errors.email` ("já está cadastrado… faça login ou
  recupere sua senha"). CPF/CRP duplicado → 409 com mensagem que não ecoa o valor. E-mail normalizado
  (trim + minúsculas). Enumeração mitigada por rate limit (ADR 004).
- **Login em duas etapas:** primeiro a senha (e-mail inexistente e senha errada têm a mesma resposta e
  custo de BCrypt equivalente via hash "dummy"); só com senha correta o status é informado:
  `ACCOUNT_INACTIVE`, `ACCOUNT_BLOCKED`, `ACCOUNT_PENDING` (403 com `code`).
- **Versão de token (`usuario.versao_token`, claim `ver`)**: conferida a cada requisição junto com o
  status. Logout (`POST /auth/logout`), troca/redefinição de senha e inativação incrementam a versão →
  todos os tokens antigos morrem na hora.
- **"Manter login"**: o backend emite token de 168 h (configurável); o cookie do front acompanha a
  expiração real do token (`expiresAt`).
- **Troca de senha obrigatória** (`troca_senha_obrigatoria`): enquanto ativa, o filtro só libera
  `/auth/change-password`, `/auth/logout` e `/users/me` (403 `PASSWORD_CHANGE_REQUIRED`).
- **Segredo JWT** sem fallback; a aplicação não sobe com segredo ausente ou < 32 caracteres.
- 401/403 da cadeia de segurança em JSON (`ErrorResponse` com `code`).

## Consequências
- Tokens emitidos antes da mudança deixam de valer se o segredo efetivo mudar (usuários fazem login de novo).
- **Ação humana:** rotacionar o segredo JWT e a senha do banco que estavam versionados.
- Verificação de e-mail no cadastro (`PENDING_VERIFICATION`) não foi implementada; o cadastro segue
  ativando a conta imediatamente (backlog).
