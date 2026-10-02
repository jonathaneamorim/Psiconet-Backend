# ADR 011 — Documentos legais: fonte única, versionada e servida pela API

- **Status:** aceito
- **Data:** 2026-10-02

## Contexto
A plataforma trata dados sensíveis de saúde e não tinha Termos de Uso, Política de Privacidade nem
Política de Cookies. Os documentos precisam refletir o que o sistema faz (inventário em
`docs/legal/inventario-de-dados.md`), ter versão e vigência, e o mesmo número de versão precisa ser
conhecido pelo backend (prova de aceite, reaceite) e pelo frontend (cadastro, banner, páginas).

## Decisão
- **Fonte única em Markdown** em `docs/legal/` (`termos-de-uso.md`, `politica-de-privacidade.md`,
  `politica-de-cookies.md`), cada um com cabeçalho `tipo/titulo/versao/vigencia` e seção
  "Histórico de versões" (changelog). O `pom.xml` empacota esses arquivos no jar (`classpath:legal/`);
  o `Dockerfile` copia `docs/legal`.
- `LegalDocumentRegistry` lê e valida os três na inicialização — **a API não sobe** com documento
  ausente, tipo divergente ou versão fora do formato `X.Y.Z`.
- Contrato público: `GET /legal/versions` e `GET /legal/documents/{terms|privacy|cookies}`
  (Markdown + versão + vigência), com `Cache-Control: max-age=300`. O frontend renderiza o Markdown
  com um renderizador próprio, sem HTML bruto (nada de `dangerouslySetInnerHTML`).
- Os textos são **minutas técnicas**: dados da empresa ficam como `[A PREENCHER: ...]` visíveis e
  todo ponto jurídico incerto está marcado "validar com jurídico".

## Como publicar uma nova versão
1. Atualize `docs/legal/inventario-de-dados.md` se o sistema mudou.
2. Edite o documento, suba `versao` (semver: MAJOR = exige reaceite com mudança relevante; a
   implementação exige reaceite para **qualquer** versão nova de Termos/Privacidade) e `vigencia`,
   e acrescente a linha no "Histórico de versões".
3. Atualize `TestData.LEGAL_VERSION` nos testes.
4. No deploy, a primeira instância que sobe registra a versão (`versao_documento_legal`) e avisa os
   usuários ativos (ADR 013). Política de Cookies nova faz o banner reaparecer (ADR 012).

## Consequências
- Mudar um documento é uma mudança de código revisada em PR, com histórico no git.
- Sem backend no ar, as páginas legais do frontend mostram erro — aceitável (a API é pré-requisito do
  site inteiro).
