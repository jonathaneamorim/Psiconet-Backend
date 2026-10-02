# ADR 015 — Cabeçalhos de segurança, menores de idade, retenção/eliminação e incidentes

- **Status:** aceito (itens jurídicos pendentes de validação)
- **Data:** 2026-10-02

## Cabeçalhos HTTP
- **API (Spring Security):** `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`
  (exceto Swagger), `Referrer-Policy: no-referrer`, `Permissions-Policy` sem câmera/microfone/
  geolocalização/pagamento/USB; `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control` e HSTS
  (em HTTPS) do próprio Spring. Respostas clínicas seguem com `no-store` (ADR 008).
- **Frontend (`next.config.ts`):** CSP só com a própria origem + origem da API em `img-src` (fotos de
  perfil); `script-src 'unsafe-inline'` é exigido pelos scripts de hidratação do Next sem nonce
  (melhoria futura: CSP com nonce no `proxy.ts`); `frame-ancestors 'none'`, `object-src 'none'`,
  `form-action 'self'`, `upgrade-insecure-requests` e HSTS em produção; `Referrer-Policy:
  strict-origin-when-cross-origin`; `X-Powered-By` desligado.
- CORS inalterado (só `FRONTEND_URL`); cookies de sessão `HttpOnly`/`Secure`/`SameSite=Lax`.
- Conferido: nenhum dado pessoal em URL de API (busca usa nome no query string — aceitável,
  não é dado sensível; e-mail aparece em `/login?email=` só após o próprio cadastro).

## Menores de idade
- Termos 4.1: cadastro só para **18+**, validado no backend (`AuthService.requireMinimumAge`) e no
  front (UX). Atendimento de menores (com responsável legal) **não é suportado** nesta versão.
- **Validar com jurídico:** a regra, o tratamento de contas de menores já existentes e um futuro
  fluxo com responsável (LGPD art. 14; normas do CFP).

## Retenção e eliminação
- Prazos sugeridos na Política de Privacidade, seção 7 (validar). Prontuário e auditorias clínicas
  **não** são apagados (triggers impedem) antes do prazo de guarda.
- **Fluxo atual de eliminação:** pedido por chamado "Privacidade / LGPD" → admin avalia →
  inativação + anonimização manual dos dados não sujeitos a guarda. **Pendente:** rotina automática
  de anonimização (substituir nome/e-mail/CPF/telefone/foto por marcadores, mantendo vínculos
  clínicos) e expurgo de notificações, sessões e logs após os prazos.

## Resposta a incidentes (procedimento básico)
1. **Detectar e conter:** revogar sessões (`/auth/logout-all` do usuário afetado; para incidente amplo,
   rotacionar `JWT_SECRET` — derruba todas as sessões), bloquear contas comprometidas, isolar o servidor.
2. **Preservar evidências:** `log_alteracao`, `auditoria_clinica` (hash encadeado), `sessao_usuario`
   (motivo `REUSE_DETECTED`), logs da aplicação e do proxy. Não apagar nada.
3. **Avaliar:** quais dados e titulares, se houve dado sensível (saúde/prontuário), risco ou dano relevante.
4. **Comunicar:** encarregado (DPO) aciona o jurídico; se houver risco ou dano relevante, comunicar a
   **ANPD e os titulares** no prazo regulamentar (LGPD art. 48; regulamento de comunicação de
   incidentes da ANPD — **validar prazo e formulário vigentes**). Mensagem aos titulares por e-mail e
   notificação, sem expor mais dados.
5. **Corrigir e registrar:** causa raiz, correção, rotação de segredos (`JWT_SECRET`,
   `CLINICAL_MASTER_KEYS` com `ClinicalDataMaintenance`, senha do banco, SMTP) e ADR com lições.
