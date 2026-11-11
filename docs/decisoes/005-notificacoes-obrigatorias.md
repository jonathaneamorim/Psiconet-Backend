# ADR 005 — Notificações obrigatórias

- **Status:** aceito
- **Data:** 2026-10-01

## Contexto
O usuário pode desligar notificações de agendamento, conexão e pagamento
(`NotificationPreference`). Os novos eventos — chamado respondido/finalizado, comunicado do
administrador e solicitação/decisão de acesso a prontuário — precisam chegar sempre.

## Decisão
- Novos tipos em `NotificationType`: `TICKET_REPLIED`, `TICKET_CLOSED`, `ADMIN_ANNOUNCEMENT`,
  `MEDICAL_RECORD_ACCESS_REQUESTED`, `MEDICAL_RECORD_ACCESS_GRANTED`, `MEDICAL_RECORD_ACCESS_DENIED`.
- `NotificationType.isMandatory()` marca como **obrigatórios**: chamados e comunicados (exigência do
  produto: canal oficial com o usuário) e prontuário (resposta a um direito do titular — LGPD art. 18 —
  e prazo de decisão do psicólogo). O `NotificationService` ignora as preferências para esses tipos.
- Não há toggle na tela de preferências para essas categorias; o DTO informa `mandatoryCategories`
  para o frontend exibir "sempre ativas".
- Texto de notificação nunca contém conteúdo clínico, texto de chamado ou motivo de negativa — só o
  aviso e o link (`referenceType` + `referenceId`).
- `PATCH /notifications/read-all` (marcar todas como lidas em uma operação) e listagem limitada às
  100 mais recentes.
- `notifications.message` ampliada para 2000 caracteres e removido o `CHECK` de enum criado pelo
  Hibernate (que recusaria os novos tipos) — via `SchemaPatches`, idempotente e sem perda de dados.

## Consequências
Usuários não conseguem silenciar avisos de suporte/comunicados/prontuário; o volume desses avisos é
controlado por rate limit (comunicados) e pelas regras antiabuso de chamados.
