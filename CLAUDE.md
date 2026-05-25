# Guia de Estilo e Padrões - Psiconet Backend

Este documento descreve os padrões de codificação, arquitetura e processos para o backend do Psiconet.

## 🏛️ Arquitetura e Organização
- **Arquitetura em Camadas:** Controller -> Service -> Repository.
- **Interfaces de Serviço:** Sempre defina uma interface em `services/interfaces` e implemente em `services/implement`.
- **Mapeamento:** Use MapStruct para todas as conversões DTO/Entidade. Evite mapeamento manual em serviços ou controllers.

## 📝 Convenções de Código
- **Nomenclatura:** CamelCase para Java, kebab-case para rotas REST.
- **Lombok:** Use `@Getter`, `@Setter`, `@NoArgsConstructor` e `@RequiredArgsConstructor` (para injeção de dependência). Evite `@Data` em entidades JPA para prevenir problemas com `hashCode`/`equals` em coleções lazy.
- **Boilerplate:** Minimize código repetitivo. Use herança de configuração no MapStruct e métodos utilitários em serviços.

## 🛡️ Segurança e Validação
- **Entrada:** Sempre valide DTOs de request com `@Valid`.
- **Papéis:** Use `@PreAuthorize` ou `SecurityConfig` para proteger endpoints.
- **Privacidade:** Certifique-se de que DTOs de resposta pública não contenham dados sensíveis (CPF, senhas, etc.).

## 🚥 Padrões REST
- **GET:** Recuperação de dados. Listagens devem ser paginadas.
- **POST:** Criação de recursos. Retorne `201 Created`.
- **PUT:** Atualização completa.
- **PATCH:** Atualização parcial.
- **DELETE:** Remoção (física ou lógica). Retorne `204 No Content`.

## ⚠️ Exceções
- Use `BusinessException` para violações de regras de negócio (HTTP 400).
- Use `EntityNotFoundException` para recursos não encontrados (HTTP 404).
- O `GlobalExceptionHandler` formatará automaticamente essas exceções para o cliente.

## 📚 Documentação
- Mantenha o Swagger atualizado através de anotações SpringDoc.
- Documente decisões arquiteturais complexas no `GEMINI.md`.
