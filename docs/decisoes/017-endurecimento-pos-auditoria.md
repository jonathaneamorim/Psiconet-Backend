# ADR 017 — Endurecimento após a auditoria de segurança de 2026-11

- **Status:** aceito
- **Data:** 2026-11-11
- **Afeta:** backend e frontend (relatórios em `docs/seguranca/auditoria-2026-11-11.md` nos dois projetos)

## Contexto
Auditoria estática dos dois projetos (prompt "Correção de bugs e auditoria de segurança"). Boa parte dos
achados já tinha sido resolvida pelos ADRs 002–015; os que se confirmaram exigiram algumas escolhas de
desenho, registradas aqui.

## Decisões

1. **Recurso de terceiros ⇒ 404; participante sem permissão do papel ⇒ 403.** O 404 é idêntico ao de id
   inexistente (não confirma existência). Quem já conhece o recurso (ex.: paciente do pagamento tentando
   aprová-lo; quem enviou a conexão tentando aceitá-la) recebe 403. O papel exigido fica **também** no
   `SecurityConfig` (por rota), porque `@PreAuthorize` roda depois do `@Valid` e o usuário sem papel
   receberia erro de validação. Matriz em `docs/seguranca/matriz-de-autorizacao.md`.
2. **Ordenação só por lista branca (`@SortableBy`)** verificada por interceptor antes da resolução dos
   argumentos; endpoint sem a anotação recusa `?sort=`. Teto de página 100 via
   `PageableHandlerMethodArgumentResolverCustomizer` — as propriedades `spring.data.web.pageable.*` não
   valem com `@EnableSpringDataWebSupport`, necessário para o formato `VIA_DTO`.
3. **`subject` do JWT = id do usuário** (antes o e-mail, legível no payload). Tokens antigos viram 401 e o
   front renova pelo refresh; nenhuma sessão é perdida.
4. **Senha ≤ 72 bytes UTF-8** (`@StrongPassword`): o BCrypt ignora o excedente; aceitar mais permitiria
   entrar com qualquer senha que compartilhe os primeiros 72 bytes.
5. **Upload:** extensão e Content-Type vêm do tipo validado por magic bytes (`StoredFileType`); imagens
   são decodificadas e regravadas (remove EXIF/GPS e cargas escondidas), com teto de 6000 px.
6. **Jobs agendados em várias instâncias:** sem ShedLock por ora. A geração recorrente trava a regra
   (`PESSIMISTIC_WRITE`); pagamento tem unicidade por consulta no banco. Os demais jobs são idempotentes
   (marcação atômica). **Suporte oficial: uma instância da API**; antes de escalar horizontalmente, avaliar
   ShedLock para não repetir trabalho.
7. **Perfil `prod`** (`ProductionConfigValidator`): a API não sobe sem segredos, URLs públicas https,
   e-mail e admin base, nem com CORS `*`, `ddl-auto=create*`, Swagger ou rate limit desligados.
8. **Swagger desligado por padrão** (`API_DOCS_ENABLED=true` só localmente).
9. **Conflito de horário do paciente com outro psicólogo não é bloqueado:** a mensagem de erro revelaria
   ao psicólogo que o paciente se consulta com outro profissional (dado de saúde de terceiro). Fica a
   critério do paciente; decisão de produto pendente se quiserem aviso só ao paciente.
10. **Testes não leem o `.env` local** (`src/test/resources/.env.properties`): o resultado da suíte não
    pode depender da máquina do desenvolvedor.

## Consequências
- Clientes que dependiam do 400 "Você não tem permissão" passam a receber 404/403 (o front já trata).
- `?sort=` com campo novo exige incluir o campo em `@SortableBy`.
- Produção precisa de `SPRING_PROFILES_ACTIVE=prod` (já no `docker-compose.prod.yml`) e das variáveis
  listadas no `.env.example`; uploads passam a um volume (`uploads_prod`).
- Continua pendente a migração versionada de schema (Flyway, ADR 001) — ver relatório.
