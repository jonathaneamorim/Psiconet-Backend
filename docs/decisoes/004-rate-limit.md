# ADR 004 — Controle de requisições (rate limit)

- **Status:** aceito
- **Data:** 2026-10-01

## Contexto
Não havia limite de requisições. Endpoints sensíveis (login, cadastro, esqueci a senha, chamados,
solicitação de prontuário, comunicados) ficavam expostos a força bruta, enumeração e spam.
O frontend chama a API **a partir do servidor Next.js**, então o IP de origem visto pela API é o do
servidor do front.

## Decisão
- **Bucket4j** (token bucket, `bucket4j_jdk17-core`) em vez de filtro próprio: algoritmo testado,
  recarga gradual (sem "rajada na virada da janela") e caminho pronto para modo distribuído
  (ProxyManager em Redis/Postgres) quando houver mais de uma instância.
- Anotação `@RateLimited("politica")` nos endpoints + `RateLimitInterceptor` (roda após o Spring
  Security): conta **por IP e por usuário autenticado**. O login tem ainda um limite por conta-alvo
  (`login-account`, chaveado pelo e-mail) contra ataques distribuídos.
- **IP real:** `ClientIpResolver` aceita `X-Forwarded-For` **apenas** quando a conexão vem de um proxy
  confiável (`TRUSTED_PROXIES`, padrão loopback = servidor Next local). O frontend repassa o IP do
  navegador nesse cabeçalho.
- Limites em **um único lugar**: `psiconet.rate-limit.*` no `application.properties`
  (desligável com `RATE_LIMIT_ENABLED=false`).
- Excedido: **HTTP 429**, cabeçalho `Retry-After` (segundos) e `ErrorResponse` com
  `code = RATE_LIMITED`; o frontend mostra toast com a mensagem.
- Regras de negócio antiabuso específicas (ex.: chamados abertos simultâneos, intervalo entre
  aberturas) ficam nos services, com valores também em properties.

## Consequências
- Buckets em memória: o limite é por instância e zera em reinício (aceitável hoje; ver backlog).
- Em produção atrás de proxy reverso, incluir o IP do proxy em `TRUSTED_PROXIES`.
