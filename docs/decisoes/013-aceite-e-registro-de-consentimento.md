# ADR 013 — Aceite no cadastro, reaceite e prova de consentimento

- **Status:** aceito
- **Data:** 2026-10-02

## Contexto
O cadastro não exigia aceite de Termos/Privacidade e não havia prova de consentimento. Usuários
antigos, administradores criados por outro administrador e qualquer usuário após nova versão
precisam aceitar antes de continuar.

## Decisão
- **Cadastro** (`PatientRegisterRequest`, `PsychologistRegisterRequest`): `acceptTerms` com
  `@AssertTrue`, mais `termsVersion`/`privacyVersion` **vistas pelo usuário**. Versão diferente da
  vigente → 409 `LEGAL_VERSION_OUTDATED` (o front recarrega e pede de novo). Nada é persistido.
- **Dados de saúde (paciente):** consentimento **específico e destacado** (LGPD art. 11, I) em
  checkbox próprio (`acceptHealthData`, `@AssertTrue`) — o vínculo de tratamento e a agenda revelam
  que a pessoa faz terapia. Registrado como `HEALTH_DATA`, sempre junto e na mesma versão da
  Privacidade. Psicólogos não têm esse item. **Validar com jurídico** se o consentimento é a base
  adequada (alternativa: tutela da saúde, art. 11, II, "f", para o que é feito pelo profissional).
- **Idade mínima de 18 anos** validada no backend pela `birthDate` (Termos, 4.1). Menores não são
  suportados nesta versão — **validar com jurídico** (LGPD art. 14).
- **Prova (`consentimento_usuario`):** usuário, tipo (`TERMS`, `PRIVACY`, `HEALTH_DATA`), versão,
  data/hora **do servidor**, IP (`ClientIpResolver`), navegador **resumido** (`UserAgentSummarizer`;
  o front repassa o User-Agent do navegador em `X-Client-User-Agent`, aceito só de proxy confiável),
  origem (`REGISTRATION`, `REACCEPTANCE`, `ADMIN_FIRST_ACCESS`). Só inserção: `@Immutable` + trigger
  no PostgreSQL. Gravado **na mesma transação** do cadastro (sem conta sem prova, sem prova sem conta),
  preservando o cadastro atômico do ADR 002. Também gera `LEGAL_TERMS_ACCEPTED` em `log_alteracao`.
- **Reaceite:** `usuario.versao_termos_aceita` / `versao_privacidade_aceita` (cópia desnormalizada)
  permitem ao `SecurityFilter` checar sem consulta extra. Pendente ⇒ 403 `CONSENT_REQUIRED` em toda a
  API, exceto `/legal/**` (documentos, status, histórico, aceite), `/users/me`, `/auth/logout*` e
  `/auth/change-password`. A troca de senha obrigatória vem antes do aceite. O login devolve
  `consentRequired` para o front ir direto à tela de aceite (`/accept-terms`).
- `GET /legal/consents/status`, `GET /legal/consents/me` (histórico sem IP/navegador),
  `POST /legal/consents` (reaceite, com as versões vistas).
- **Aviso de nova versão:** `LegalVersionPublisher` registra as versões em `versao_documento_legal`
  na inicialização; versão nova de Termos/Privacidade (havendo anterior) gera, uma única vez,
  notificação obrigatória `LEGAL_DOCUMENTS_UPDATED` (`referenceType = LEGAL`) e e-mail
  `documentos-atualizados` (só o nome dos documentos) para os usuários ativos.
- **Canal do titular:** nova categoria de chamado `PRIVACY` ("Privacidade / LGPD").

## Consequências
- Todo usuário existente (inclusive admins) verá a tela de aceite no próximo acesso após o deploy.
- O envio do aviso é síncrono na inicialização e percorre todos os usuários ativos; para bases
  grandes, mover para job em lotes (pendência registrada).
