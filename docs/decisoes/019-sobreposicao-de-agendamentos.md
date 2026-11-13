# ADR 019 — Sobreposição de agendamentos: permitida com confirmação e sinalizada

- **Status:** aceito (itens marcados com ⚠ dependem de confirmação do dono do produto)
- **Data:** 2026-11-12
- **Afeta:** backend e frontend

## Contexto
O backend bloqueava qualquer consulta no mesmo horário de outra ("Já existe um agendamento neste
horário."), e a recorrência **pulava em silêncio** a ocorrência em conflito (só `log.warn`): a série perdia
sessões sem ninguém saber. O psicólogo precisa poder atender em horários sobrepostos (encaixe, grupo,
remarcação provisória), mas sem fazê-lo por engano.

## Decisão
1. **Outro paciente no mesmo horário: permitido, com confirmação explícita.** Sem `acknowledgeOverlap=true`
   a API responde **409 `SCHEDULE_OVERLAP`** com `conflicts` (id, início, fim, título, nome do paciente) e não
   cria nada. A API é a autoridade: um cliente que ignore a tela não cria sobreposição sem confirmar.
2. ⚠ **Mesmo paciente no mesmo horário: bloqueado** (400 no campo `startDateTime`/`startDate`), mesmo com
   confirmação — é duplicidade, não "dois atendimentos".
3. **O que conta:** para criar, toda consulta **não cancelada**. ⚠ Para o **alerta** visível, só consultas
   `ACCEPTED` ainda não terminadas (histórico `COMPLETED`/`NO_SHOW` não alerta). Consultas encostadas
   (fim = início) não se sobrepõem.
4. **Calculada, nunca armazenada:** `hasOverlap`/`overlappingAppointments` são recalculados a cada leitura
   (lote: uma consulta por janela de datas + varredura em memória). Cancelar uma consulta apaga o alerta.
   Índice novo `idx_agendamento_vinculo_inicio` (vínculo, início), criado de forma aditiva.
5. **Sinalização em três camadas:** aviso em tempo real no formulário (`GET /appointments/overlaps`, com
   rate limit `schedule-overlap-check`) → confirmação ao salvar (409) → selo persistente na agenda do
   psicólogo. **Recorrência:** a ocorrência sobreposta é **gerada** e o psicólogo recebe a notificação
   `SCHEDULE_OVERLAP` (uma por ocorrência; a geração é travada por regra). A 1ª ocorrência da série segue a
   regra da consulta avulsa (exige confirmação). Preview: `GET /recurrence-rules/{id}/preview/overlaps` e,
   para o rascunho do formulário, `POST /treatment-links/{id}/recurrence-rules/overlaps` (próximos 90 dias).
   O preview antigo (`/preview`, lista de datas) não mudou de formato.
6. **Privacidade:** só o psicólogo dono vê a sobreposição e o nome do outro paciente. O `AppointmentDTO`
   do paciente não tem os campos (omitidos do JSON); notificações e e-mails citam só data/hora.
7. **Concorrência:** a criação continua travando o psicólogo (`findByIdForUpdate`); a geração recorrente
   passou a usar o mesmo mutex — duas criações simultâneas nunca duplicam o mesmo paciente.

## Consequências
- O psicólogo pode ter duas salas de vídeo no mesmo horário (cada consulta tem a sua); o selo avisa que ele
  não estará nas duas ao mesmo tempo. Pagamentos, estatísticas, lembretes e ciclo de vida seguem por consulta.
- A notificação `SCHEDULE_OVERLAP` é da categoria "agendamento" (respeita a preferência do psicólogo).
- Contrato: campos novos só na visão do psicólogo; código de erro novo `SCHEDULE_OVERLAP` (409).
