# Matriz de autorização (backend)

> Atualizada em 2026-11-11 (auditoria, ADR 017). Fonte: `SecurityConfig` (rota), `@PreAuthorize`
> (controller) e checagens de dono/vínculo nos services. Cobertura: `AuthorizationMatrixIntegrationTest`,
> `AdminAuthorizationIntegrationTest`, `MedicalRecordIntegrationTest`, `TicketFlowIntegrationTest`,
> `VideoSessionIntegrationTest`, `LegalConsentIntegrationTest`.

**Legenda:** Pub = público · PAC = paciente · PSI = psicólogo · ADM = admin.
**Dono** = regra de propriedade no service. Recurso de terceiros ⇒ **404**; papel errado ⇒ **403**;
sem token ⇒ **401**. Todas as rotas autenticadas também exigem conta `ACTIVE`, versão de token e sessão
válidas, troca de senha resolvida e aceite dos documentos vigentes (`SecurityFilter`).

| Endpoint | Pub | PAC | PSI | ADM | Regra de propriedade |
|---|:-:|:-:|:-:|:-:|---|
| `POST /auth/register/*`, `/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password*` | ✔ | | | | rate limit por IP/conta |
| `POST /auth/logout` | ✔ | ✔ | ✔ | ✔ | encerra só a sessão do token |
| `POST /auth/change-password`, `/auth/logout-all` | | ✔ | ✔ | ✔ | o próprio usuário |
| `GET /users/me` | | ✔ | ✔ | ✔ | o próprio usuário |
| `GET /legal/versions`, `/legal/documents/{slug}` | ✔ | | | | — |
| `GET/POST /legal/consents*` | | ✔ | ✔ | ✔ | o próprio usuário |
| `GET /public/photos/{arquivo}` | ✔ | | | | só imagens de `avatars/` |
| `GET /patients/me`, `PATCH /patients/me/profile`, `POST /patients/me/photo` | | ✔ | | | o próprio perfil |
| `GET /patients/{id}`, `/patients/search` | | ✔ | ✔ | ✔ | expõe só nome, foto, cidade/UF e status de conexão |
| `GET /psychologists/me`, `PATCH /psychologists/me/*`, `POST /psychologists/me/photo` | | | ✔ | | o próprio perfil |
| `GET /psychologists/{id}`, `/psychologists/search` | | ✔ | ✔ | ✔ | perfil público |
| `POST /connections/{targetUserId}` | | ✔ | ✔ | | — |
| `PATCH /connections/{id}/accept`, `/reject` | | ✔ | ✔ | | só quem recebeu (remetente ⇒ 403; terceiro ⇒ 404) |
| `DELETE /connections/{id}`, `GET /connections`, `/connections/pending` | | ✔ | ✔ | | remetente ou destinatário |
| `PATCH /treatment-links/{id}/price` | | | ✔ | | psicólogo do vínculo |
| `POST /treatment-links/{id}/recurrence-rules` | | | ✔ | | psicólogo do vínculo, vínculo ativo |
| `GET /treatment-links/{id}/recurrence-rules` | | ✔ | ✔ | | participante do vínculo |
| `PATCH /recurrence-rules/{id}/deactivate` | | | ✔ | | psicólogo do vínculo (paciente ⇒ 403) |
| `GET /recurrence-rules/{id}/preview` | | ✔ | ✔ | | participante do vínculo |
| `POST /appointments` | | | ✔ | | vínculo ativo com o paciente |
| `GET /appointments`, `/appointments/stats` | | ✔ | ✔ | | só os próprios |
| `GET /appointments/{id}`, `PATCH /appointments/{id}/cancel` | | ✔ | ✔ | | participante do vínculo |
| `POST /appointments/{id}/video/join` | | ✔ | ✔ | | participante, vínculo ativo, janela aberta (ADR 016) |
| `POST /appointments/{id}/video/end` | | | ✔ | | psicólogo do vínculo |
| `GET /video/config` | | ✔ | ✔ | ✔ | — |
| `POST /webhooks/video` | ✔ | | | | assinatura do provedor obrigatória |
| `GET /payments`, `/payments/summary`, `/payments/{id}`, `/payments/{id}/receipt` | | ✔ | ✔ | | participante do vínculo |
| `POST /payments/{id}/receipt`, `/payments/{id}/dispute` | | ✔ | | | paciente do vínculo (psicólogo ⇒ 403) |
| `PATCH /payments/{id}/approve`, `/reject` | | | ✔ | | psicólogo do vínculo (paciente ⇒ 403) |
| `GET/PATCH /notifications*` | | ✔ | ✔ | ✔ | só as próprias (terceiro ⇒ 404) |
| `/tickets/**` | | ✔ | ✔ | | autor do chamado |
| `/medical-records/**` | | | ✔ | | psicólogo titular do vínculo (ADRs 008–010) |
| `/patient/medical-records/**` | | ✔ | | | paciente titular, acesso liberado pelo psicólogo |
| `/admin/**` | | | | ✔ | admin nunca vê conteúdo clínico; CPF mascarado |
