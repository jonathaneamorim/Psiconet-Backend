# ADR 018 — Spring Boot 4.1 e Java 25

- **Status:** aceito
- **Data:** 2026-11-12
- **Afeta:** backend (o frontend não muda: contrato JSON idêntico)

## Contexto
O backend estava no Spring Boot **3.3.5**, cuja linha perdeu o suporte gratuito em 30/06/2025. A consulta
OSV da árvore de dependências encontrou **100 vulnerabilidades conhecidas (13 críticas)** — Tomcat
10.1.31, Spring Security 6.3.4 (inclui CVE-2025-22228), Spring Framework 6.1.14, Thymeleaf 3.1.2,
Jackson 2.17.2, pgjdbc 42.7.4, logback 1.5.11.

### Evidência da escolha (consultada em 12/11/2026)
| Linha | Suporte OSS até | Última versão | Fonte |
|---|---|---|---|
| 4.2 | — | 4.2.0-RC2 (não GA) | Maven Central (`maven-metadata.xml`) |
| **4.1** | **31/07/2027** | **4.1.2** (22/10/2026) | endoflife.date/spring-boot; spring.io/projects/spring-boot (exibe 4.1.2) |
| 4.0 | 31/12/2026 | 4.0.9 | endoflife.date |
| 3.5 | 30/06/2026 (encerrado) | 3.5.16 | endoflife.date |

## Decisão
- **Spring Boot 4.1.2** (Spring Framework 7, Spring Security 7, Hibernate ORM 7, Spring Data 2025.x,
  Jackson 3, Tomcat 11). A 4.2 só depois de GA + primeiro patch; a 4.0 expira em semanas.
- **Caminho em etapas com tag de retorno:** `upgrade-0-baseline` → `upgrade-1-boot-3.5.16` (remoção das
  APIs depreciadas) → `upgrade-2-boot-4.1.2` → `upgrade-3-java-25`.
- **Java 25 (LTS).** Lombok 1.18.48 e MapStruct 1.6.3 compilam sem aviso; imagens oficiais
  `maven:3.9.16-eclipse-temurin-25` e `eclipse-temurin:25-jre` existem; suíte verde no JDK 25.0.5 (imagem)
  e no JDK 26 (máquina de desenvolvimento). Java 21 não traria vantagem e tem suporte mais curto.
- **Bibliotecas trocadas:** `spring-boot-starter-web` → `-webmvc`; testes com `spring-boot-starter-webmvc-test`
  e `spring-boot-starter-security-test`; springdoc 2.6.0 → **3.2.0** (matriz oficial: Boot 4.1 ⇒ 3.1.x–3.2.x);
  `spring-dotenv` 4.0.0 → **`springboot4-dotenv` 5.1.0** (nos testes, `springdotenv.enabled=false` no Surefire);
  java-jwt 4.6.2; Maven 3.9.16.
- **Jackson 3 no código** (`tools.jackson`), sem o módulo de compatibilidade `spring-boot-jackson2`. O
  Jackson 2 continua no classpath só como dependência interna do java-jwt.
- **Proteções de regressão:** `ApiContractSnapshotTest` (forma de 30 respostas, gerada na 3.5.16 e
  idêntica na 4.1.2) e `ClinicalHashStabilityTest` (JSON canônico e hash SHA-256 do prontuário fixos —
  uma mudança faria anotações antigas parecerem adulteradas).

## Consequências
- Schema intacto: a 4.1.2 sobe com `ddl-auto=validate` contra cópia do banco de desenvolvimento.
- Vulnerabilidades da árvore de execução: 100 → **1 moderada** (`ognl` 3.3.4, trazido pelo Thymeleaf
  3.1.5). A correção (ognl 3.4.x) quebra o Thymeleaf 3.1 (`NoSuchMethodError` em `OgnlContext`); o aviso
  exige expressão OGNL controlada pelo atacante e os templates de e-mail são fixos (dado do usuário só
  via `th:text`). Acompanhar o Thymeleaf 3.2.
- Inicialização ~2,5 s mais lenta na máquina local (7,5 s → 10,0 s); tempo das consultas igual.
  Na imagem com JRE 25, 7,4 s.
- OpenAPI passa para 3.1.0 com esquemas mais precisos (página tipada por DTO, `format: email`,
  `LocalTime` como string); mesmos 89 caminhos e operações.
- Acompanhar: GA da 4.2 (prevista para nov/2026); fim do OSS da 4.1 em 31/07/2027.
- **Rollback:** `git checkout upgrade-1-boot-3.5.16` (ou `pre-upgrade-spring`) e rebuild da imagem;
  nenhuma mudança de banco a desfazer.
