# ADR 009 — Relação paciente × psicólogo no prontuário

- **Status:** aceito (**requer validação jurídica antes de produção**)
- **Data:** 2026-10-01

## Decisão
- **Um prontuário por vínculo** paciente–psicólogo (`TreatmentLink`).
- Duas camadas (`SessionNote.type`):
  - **RECORD — registro documental** (campos do prontuário + registros de evolução finalizados e seus
    adendos): pode ser liberado ao paciente;
  - **PRIVATE — anotações pessoais de trabalho** do psicólogo: nunca entregues automaticamente.
  Rascunhos nunca integram o prontuário.
- **O paciente não tem acesso automático.** Ele **solicita** (direito de acesso do titular — LGPD art. 18, II)
  → o psicólogo é notificado (site + e-mail genérico, sem nome do paciente nem conteúdo)
  → o psicólogo **libera** (somente leitura, prazo opcional de até 365 dias, exportação em PDF opcional)
  ou **nega com justificativa obrigatória**, cifrada e visível ao paciente **apenas na plataforma**
  → o paciente é notificado (site + e-mail sem conteúdo clínico). Uma solicitação pendente por vez.
- O paciente **nunca edita**. Tudo é auditado (`auditoria_clinica`).
- Vínculo encerrado: o psicólogo mantém o prontuário (guarda legal) e o paciente continua podendo solicitar.
- PDF exportado contém identificação, datas (servidor) e hash SHA-256 de cada registro; o hash do
  arquivo vai no cabeçalho `X-Document-SHA256` e na auditoria.

## Pontos para validação jurídica/CRP
- Se o paciente tem direito à cópia integral (incluindo anotações pessoais) e em que prazo a resposta
  deve ser dada; se a negativa é admissível e em quais hipóteses (o desenho registra justificativa, mas
  não decide a legalidade da negativa).
- Se a liberação com prazo é adequada ou se a cópia deve ser definitiva.
- Acesso por representante legal (menores/incapazes) — **não implementado**.
