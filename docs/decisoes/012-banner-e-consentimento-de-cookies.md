# ADR 012 — Banner e gerenciamento de consentimento de cookies

- **Status:** aceito
- **Data:** 2026-10-02

## Contexto
Não havia aviso de cookies. O inventário (`docs/legal/inventario-de-dados.md`) mostrou que a Psiconet
usa **apenas cookies estritamente necessários** e nenhum terceiro (sem analytics, pixels, CDNs ou
embeds; fonte servida pelo próprio domínio). Mesmo assim, a LGPD e o guia orientativo de cookies da
ANPD recomendam transparência e uma forma simples de gerenciar a escolha *(confirmar a versão vigente
do guia — validar com jurídico)*.

## Decisão (implementação no frontend)
- **Banner** no primeiro acesso (inclusive login/cadastro), texto curto, link para `/cookies` e três
  ações com **o mesmo estilo**: "Rejeitar não essenciais", "Personalizar", "Aceitar todos". Não bloqueia
  o uso do site (`role="region"`, não modal).
- **Modal de personalização** (`role="dialog"`, Esc fecha, foco no painel): categorias necessários
  (travada e explicada), preferências, estatísticos e marketing — as três últimas aparecem como
  **"Não utilizado"** e desabilitadas, com a lista real de cookies da categoria necessária.
- **Persistência:** cookie próprio `psiconet_consent` (`SameSite=Lax`, `Secure` em produção, sem
  `HttpOnly` para scripts futuros respeitarem a escolha, **180 dias**) com `{ v: versão da Política de
  Cookies, c: categorias, d: data }`. Gravado por **Server Action**: versão e data vêm do servidor;
  categorias são filtradas pelas existentes. Versão diferente da vigente (`GET /legal/versions`) ⇒ o
  banner reaparece.
- **SSR-safe:** o layout raiz lê o cookie no servidor e passa ao `CookieConsentProvider`, então o
  banner sai no HTML inicial (sem "piscar" na hidratação).
- **Reabrir/revogar:** botão "Preferências de cookies" no rodapé global e em
  "Configurações → Privacidade e consentimentos".
- **Futuro:** nenhum script não essencial pode ser carregado fora de `<ConsentGate category=...>` /
  `useCookieConsent().hasConsent(...)`; incluir um terceiro exige marcar a categoria como disponível em
  `lib/cookieConsent.ts`, nova versão da Política de Cookies e liberar a origem na CSP (ADR 015).
  **Nenhum rastreador foi criado.**

## Consequências
- Hoje "Aceitar todos" e "Rejeitar" gravam o mesmo conteúdo (só necessários) — a escolha fica
  registrada e o sistema está pronto para categorias futuras.
- Sem a API (versões indisponíveis) o banner não aparece, pois não há versão para registrar.
