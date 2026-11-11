# ADR 007 — Chamados de suporte

- **Status:** aceito
- **Data:** 2026-10-01

## Decisão
- Entidades `Ticket` (`chamado`) e `TicketMessage` (`chamado_mensagem`, `@Immutable`). Status
  `OPEN → IN_PROGRESS → RESOLVED → CLOSED`; resposta do usuário em chamado `RESOLVED` reabre (`OPEN`);
  `CLOSED` é terminal. Protocolo legível `CH-AAAAMMDD-XXXXXX`.
- Paciente/psicólogo: `/tickets` (abrir, listar, detalhar, responder, encerrar). Busca sempre por
  `id + solicitante` → chamado de outra pessoa responde **404** (anti-IDOR, não confirma existência).
- Admin: `/admin/tickets` com filtros (status, prioridade, categoria, busca), resposta, mudança de
  status/prioridade. Mensagens da administração aparecem ao usuário como "Equipe Psiconet".
- **Resposta ou finalização (RESOLVED/CLOSED) pelo admin ⇒ notificação obrigatória + e-mail** — o
  e-mail traz só o protocolo e o link (nunca o texto do chamado).
- **Antiabuso** (valores em `psiconet.tickets.*` e `psiconet.rate-limit.*`):
  rate limit por IP/usuário (`ticket-open` 5/h, `ticket-message` 20/10min); no máximo 3 chamados ativos
  por usuário; intervalo mínimo de 2 min entre aberturas (429 + Retry-After); tamanhos máximos
  (assunto 120, descrição/mensagem 4000) validados no DTO e após sanitização; sanitização de texto
  (controle/invisíveis/quebras excessivas; conteúdo tratado como texto puro e escapado na renderização);
  duplicidade por hash SHA-256 normalizado (mesmo chamado em 24 h; mesma mensagem em 10 min → 409).
- Dados de exemplo apenas no perfil `dev` (`DevDataSeeder`, `SPRING_PROFILES_ACTIVE=dev`).
- Suporte não é canal clínico: administradores nunca acessam prontuário por aqui.
