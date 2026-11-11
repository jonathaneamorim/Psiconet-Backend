# ADR 008 — Armazenamento seguro do prontuário e das anotações de sessão

- **Status:** aceito (**requer validação jurídica e do CRP antes de produção**)
- **Data:** 2026-10-01

## Contexto
Prontuário e anotações são dados pessoais **sensíveis de saúde** (LGPD — Lei 13.709/2018, art. 5º, II, e
art. 11) protegidos por **sigilo profissional** (Código de Ética do Psicólogo — Res. CFP 010/2005). Antes,
`MedicalRecord` e `SessionNote` guardavam texto puro, sem controle de acesso nem auditoria.

## Decisão
**Criptografia em nível de campo — AES-256-GCM com envelope encryption** (`ClinicalCryptoService`)
- Cada prontuário tem uma **chave de dados (DEK)** de 256 bits; o banco guarda só a DEK **cifrada pela
  chave-mestra (KEK)** + a **versão** da KEK (`chave_dados_cifrada`, `versao_chave_mestra`).
- Cada campo é cifrado com IV aleatório e **AAD** = contexto (`record:<id>:<campo>`,
  `note:<id>:content`, ...): cifra copiada para outro registro/campo não abre; adulteração é detectada (tag GCM).
- KEKs vêm de `CLINICAL_MASTER_KEYS` (`v1:<base64>,v2:<base64>`) e `CLINICAL_ACTIVE_KEY_VERSION`,
  fora do banco. A interface `MasterKeyProvider` foi desenhada para **KMS** (AWS/GCP KMS, Vault
  Transit) sem mudar o resto. **Rotação:** adicionar nova versão, torná-la ativa e reiniciar — o
  `ClinicalDataMaintenance` re-cifra as DEKs (os dados não são reprocessados).
- Sem chave configurada, as funcionalidades clínicas respondem **503** — nunca caem para texto claro.
- Nada clínico em texto claro no banco, backups, logs, cache, notificações, e-mails ou mensagens de erro.
  Respostas clínicas com `Cache-Control: no-store` (`ClinicalNoStoreFilter`).

**Acesso**
- Somente o **psicólogo titular** do vínculo lê/edita; outros recebem **404** (não confirma existência)
  e a tentativa é auditada. `@PreAuthorize` por role nos controllers + checagem de titularidade no service.
- **Administradores não acessam conteúdo clínico** em nenhum endpoint (nem pelo suporte) — testado.
- Vínculo encerrado: o psicólogo mantém a guarda (leitura + adendos), sem novas anotações.

**Auditoria imutável** (`auditoria_clinica`): todo acesso e alteração (visualização, criação, rascunho,
finalização, adendo, solicitação/decisão do paciente, PDF, tentativas negadas) com ator, papel, ação,
alvo, IP e data. Append-only: entidade `@Immutable`, **trigger no Postgres** recusando UPDATE/DELETE e
**encadeamento de hash** (`ClinicalAuditService.verifyChain()`).

**Sem exclusão física**: triggers recusam DELETE em `prontuario` e `anotacoes_sessao` e UPDATE em
anotação finalizada; adendos e auditoria são imutáveis.

**Migração**: colunas legadas de texto puro são cifradas no novo formato e só então esvaziadas, de
forma idempotente na inicialização (no banco de dev não havia registros).

## Retenção — normas a confirmar (**validar com jurídico/CRP**)
- Res. CFP 001/2009 (alterada pela Res. CFP 005/2010): obrigatoriedade do registro documental/prontuário.
- Res. CFP 006/2019: elaboração de documentos e prazo mínimo de guarda (citado comumente como 5 anos).
- Lei 13.787/2018 (digitalização e guarda de prontuário do paciente): avaliar aplicabilidade e prazo.
- Res. CFP 11/2018 (serviços psicológicos por meios de tecnologia).
- LGPD art. 16 (conservação para cumprimento de obrigação legal) e art. 37/46 (registro e segurança).
Não implementamos expurgo automático: o sistema **não apaga** dados clínicos; a política de retenção
(e eventual anonimização após o prazo) depende da validação acima — backlog.

## Consequências / riscos residuais
- KEK em variável de ambiente é inferior a um KMS gerenciado (backlog prioritário).
- Lock da auditoria é por instância; com várias instâncias, usar advisory lock/sequência do banco.
- Perda das KEKs = perda dos dados clínicos: KEKs precisam de backup seguro separado do banco.
