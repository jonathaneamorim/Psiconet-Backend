# Guia de Estilo e Padrões - Psiconet Backend

Este documento descreve os padrões de codificação, arquitetura e processos para o backend do Psiconet.

## 🏛️ Arquitetura e Organização
- **Arquitetura em Camadas:** Controller -> Service -> Repository.
- **Interfaces de Serviço:** Sempre defina uma interface em `services/interfaces` e implemente em `services/implement`.
- **Mapeamento:** Use MapStruct para todas as conversões DTO/Entidade. Evite mapeamento manual em serviços ou controllers.

## 📝 Convenções de Código
- **Nomenclatura:** CamelCase para Java, kebab-case para rotas REST.
- **Lombok:** Use `@Getter`, `@Setter`, `@NoArgsConstructor` e `@RequiredArgsConstructor`. Evite `@Data` em entidades JPA.
- **DTOs de Resposta:** Devem ser imutáveis sempre que possível ou utilizar o padrão Builder.

## 🛡️ Segurança e Validação
- **Entrada:** Valide DTOs de request com `@Valid`. Use mensagens em português.
- **Contexto de Relacionamento:** DTOs de perfil e busca devem incluir obrigatoriamente `connectionStatus` e `connectionId` (quando aplicável) para servir como fonte de verdade para o frontend.
- **Campos Sensíveis:** Senhas, CPFs e dados privados nunca devem ser expostos em DTOs de resposta pública.

## 🚥 Padrões REST

### Verbos e Respostas
- **GET:** Recuperação de dados. Listagens devem ser paginadas (`Page`).
- **POST:** Criação de recursos (`201 Created`) ou ações complexas (`200 OK`).
- **PUT:** Atualização completa (`204 No Content`).
- **PATCH:** Atualização parcial (`204 No Content`).
- **DELETE:** Remoção ou cancelamento (`204 No Content`).

### Estrutura de Resposta de Erro (`ErrorResponse`)
```json
{
  "status": 400,
  "message": "Mensagem de erro",
  "timestamp": "2026-05-25T10:00:00",
  "errors": {
    "campo": "detalhe do erro"
  }
}
```

## 📚 Documentação
- Mantenha o Swagger atualizado.
- Decisões de negócio e infraestrutura devem estar no `GEMINI.md`.
- Este arquivo (`CLAUDE.md`) foca em padrões de código e implementação.
