# ADR 014 — Sessão: token de acesso curto + refresh rotativo por dispositivo ("manter conectado")

- **Status:** aceito (complementa o ADR 002)
- **Data:** 2026-10-02

## Contexto
Situação encontrada na revisão (vários achados do pedido original já estavam corrigidos pelo ADR 002):
- O segredo JWT **já não tinha** valor padrão e a API já não subia sem `JWT_SECRET` ≥ 32 caracteres.
- O `proxy.ts` **já** checava `exp` do token; o cookie **já** acompanhava `expiresAt` do token.
- "Manter login" emitia um JWT de **168 h**: um token de acesso de 7 dias, sem renovação, sem
  revogação por dispositivo (o logout incrementava `versao_token` e derrubava **todos** os dispositivos).
- Sem "manter", o token de 2 h vencia no meio do uso, sem renovação.
- O front ainda tinha durações próprias de fallback (`ACCESS_TIME=180 min`, `KEEP_LOGGED_TIME=7 d`)
  contraditórias com o backend, e um 401 só apagava o cookie (sem redirecionar nem explicar).

## Decisão
- **Token de acesso** JWT HS256 de **15 min**, com claim `sid` (id da sessão). O filtro confere
  a cada requisição: assinatura/expiração, usuário `ACTIVE`, `ver` = `usuario.versao_token` **e
  sessão não revogada** — logout de um dispositivo derruba o acesso dele na hora. Tokens sem `sid`
  (formato antigo) são recusados: todos fazem login de novo após o deploy.
- **Refresh token** `<sessionId>.<segredo de 256 bits>`; só o **SHA-256** do segredo fica em
  `sessao_usuario`. **Rotação** a cada uso; reapresentar o segredo anterior **dentro de 30 s**
  (requisições paralelas do Next) devolve só um novo acesso (refresh inalterado); qualquer outro
  segredo ⇒ **reutilização detectada**: a sessão é revogada (`REUSE_DETECTED`) e auditada.
- **Durações** (única configuração, `psiconet.session.*`; o front não tem durações próprias):

  | Modo | Cookies no navegador | Inatividade | Limite absoluto |
  |---|---|---|---|
  | Sem "manter conectado" | **cookies de sessão** (somem ao fechar o navegador) | 2 h | 12 h |
  | "Manter conectado neste dispositivo" | persistentes (acompanham a expiração da API) | 7 dias, renovado a cada uso | 30 dias |

  Optamos por ter refresh **também** sem "manter conectado" (o pedido original sugeria cookie de
  sessão sem refresh): sem ele, o token de acesso teria de durar a sessão inteira (horas), o que é
  pior em segurança; com ele, o acesso é de 15 min e a inatividade de 2 h funciona como
  **encerramento por inatividade** — relevante nas telas com dado clínico.
- **Revogação:** `POST /auth/logout` (este dispositivo), `POST /auth/logout-all` (todos:
  incrementa `versao_token` + revoga sessões), troca de senha (revoga todas e recria a do
  dispositivo atual mantendo o modo), redefinição de senha, inativação/bloqueio e troca de e-mail
  pelo admin (revogação explícita, motivo `ADMIN`). A renovação também exige usuário ativo e a mesma
  `versao_token` do login.
- `POST /auth/refresh` é público (o refresh é a credencial), com rate limit `refresh` e
  `SessionService.refresh` sob `SELECT ... FOR UPDATE` na sessão.
- **Frontend:** o `proxy.ts` renova de forma transparente quando o acesso venceu (ou vence em < 60 s)
  e há refresh — grava os cookies na resposta e repassa o novo token à própria requisição. 401 com
  sessão inválida ⇒ cookies apagados e redirecionamento para `/login?sessao=expirada`.
- Rótulo: **"Manter conectado neste dispositivo"** com aviso "Não use em computadores compartilhados".
- Cabeçalhos de segurança da API: ver ADR 015.

## Consequências
- Uma consulta a mais por requisição autenticada (sessão por id, chave primária).
- Pendências: tela "dispositivos conectados" (os dados já existem: criação, último uso, navegador
  resumido, IP); expurgo de sessões expiradas após o prazo de guarda de registros de acesso.
