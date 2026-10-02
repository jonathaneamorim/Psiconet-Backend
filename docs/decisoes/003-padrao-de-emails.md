# ADR 003 — Padrão único de e-mails transacionais

- **Status:** aceito
- **Data:** 2026-10-01

## Contexto
Havia um único e-mail (redefinição de senha) com HTML montado em `String.formatted`, escape manual
parcial e envio disparado dentro da transação. A evolução exige vários e-mails novos.

## Decisão
- **Thymeleaf (somente o core)** como motor de templates: escapa HTML por padrão em `th:text`,
  suporta fragmentos (layout + componentes) e tem modo TEXT para a versão texto simples. Sem o starter,
  para não registrar view resolvers MVC numa API REST.
- Diretório `src/main/resources/templates/email/`:
  - `layout/base.html` — cabeçalho com a identidade Psiconet, corpo, rodapé com aviso de e-mail
    automático (derivado do e-mail de recuperação de senha original);
  - `components/` — `botao` (CTA + link alternativo), `destaque`, `paragrafo` (saudação/nota);
  - um template por e-mail (`recuperacao-senha`, `boas-vindas`, `convite-administrador`,
    `chamado-respondido`, `chamado-finalizado`, `comunicado`, `prontuario-solicitado`,
    `prontuario-respondido`) + versão `text/<nome>.txt`.
- Envio: `EmailService` (fachada por caso de uso) publica `EmailRequestedEvent`; o `EmailDispatcher`
  envia com `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` + `@Async` no pool
  `mailTaskExecutor`, como `multipart/alternative` (texto + HTML) e cabeçalho `Auto-Submitted`.
- Falhas: log com template e destinatário mascarado (`m***@dominio`), sem conteúdo.
- Sem `MAIL_FROM` o envio é desabilitado com aviso (ambiente local/teste).
- **Nenhum e-mail contém conteúdo clínico**, nome de paciente em e-mail para psicólogo, texto de
  chamado ou motivo de negativa: e-mails só avisam e levam à plataforma (acesso autenticado/auditado).

## Consequências
- Novo e-mail = 1 item no enum `EmailTemplate` + `.html` + `.txt` + método na fachada. O teste
  `EmailRendererTest` renderiza todos os templates e verifica escape de HTML.
