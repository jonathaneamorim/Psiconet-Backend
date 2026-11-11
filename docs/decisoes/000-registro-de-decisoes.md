# ADR 000 — Registro de decisões arquiteturais

- **Status:** aceito
- **Data:** 2026-10-01

## Contexto
Decisões técnicas e de produto estavam espalhadas entre `GEMINI.md`, comentários e conversas.

## Decisão
Toda decisão relevante passa a ser registrada como um ADR curto em `docs/decisoes/NNN-titulo.md`,
no projeto afetado (quando afeta os dois, o ADR fica no backend e o frontend referencia).

Formato: **Status**, **Data**, **Contexto**, **Decisão**, **Consequências** (e, quando houver,
**Alternativas consideradas**). ADRs não são reescritos: uma mudança de rumo gera um novo ADR que
"substitui" o anterior.

## Consequências
`GEMINI.md` foi removido; o conteúdo útil migrou para o `CLAUDE.md` e para estes ADRs.
