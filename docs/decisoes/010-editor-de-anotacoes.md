# ADR 010 — Editor de anotações e integridade dos registros

- **Status:** aceito
- **Data:** 2026-10-01

## Decisão
- **Editor:** TipTap 3 (ProseMirror) no frontend — compatível com React 19/Next 16 (`immediatelyRender:
  false` para SSR), extensões só do necessário: título (H1), subtítulo (H2), parágrafo, negrito, itálico,
  sublinhado, lista ordenada/não ordenada, desfazer/refazer; atalhos de teclado nativos; autosave do
  rascunho com debounce (detalhes no ADR do frontend).
- **Armazenamento:** conteúdo **estruturado (JSON do editor)**, nunca HTML. O backend
  (`RichTextSanitizer`) **reconstrói** o documento a partir de uma whitelist de nós/marcas/atributos
  (rejeita o resto com 400), limita tamanho/profundidade e serializa de forma **canônica**.
  A renderização (front e PDF) percorre o JSON — sem `dangerouslySetInnerHTML`.
- **Validade e integridade:**
  - data/hora do **servidor**; autor com **nome + CRP congelados** na finalização;
  - **hash SHA-256** sobre o JSON canônico + metadados (id, prontuário, consulta, tipo, título, autor,
    CRP, data) — recalculado a cada leitura (`integrityVerified`);
  - **finalizado = imutável** (service + trigger no banco); correções por **adendo/retificação**
    numerado, datado, assinado (nome + CRP) e com hash **encadeado** ao registro anterior;
  - histórico preservado: nada é sobrescrito nem apagado.
- `SessionNote` evoluiu sem perda: mesmas tabela e colunas legadas (migradas para o formato cifrado),
  `@OneToOne` → `@ManyToOne` com a consulta (registro documental e anotação pessoal na mesma sessão).

## Backlog
Assinatura digital ICP-Brasil (MP 2.200-2/2001) para validade jurídica plena dos documentos exportados;
carimbo do tempo.
