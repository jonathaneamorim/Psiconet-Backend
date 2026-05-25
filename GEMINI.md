# Projeto Psiconet - Documentação do Backend

Este documento é a fonte oficial de verdade para desenvolvedores e agentes de IA trabalhando no Backend do Psiconet.

## 🚀 Visão Geral do Projeto
O Psiconet é uma plataforma que conecta psicólogos e pacientes, facilitando agendamentos, gestão de prontuários e comunicação segura. O backend é construído com **Spring Boot 3.3.5** e segue uma arquitetura em camadas limpa, escalável e segura.

## 🛠 Stack Tecnológica
- **Linguagem:** Java 17
- **Framework:** Spring Boot 3.3.5
- **Segurança:** Spring Security com JWT (Stateless)
- **Banco de Dados:** PostgreSQL
- **Persistência:** Spring Data JPA (Hibernate)
- **Mapeamento:** MapStruct & Lombok
- **Documentação:** SpringDoc OpenAPI (Swagger)
- **Validação:** Bean Validation (Hibernate Validator)
- **Logging:** SLF4J com Lombok (@Slf4j)

## 📁 Estrutura do Projeto
O projeto está organizado por domínios e responsabilidades:
- `src/main/java/com/psiconet/`
    - `controllers/`: Endpoints REST (Auth, User, Admin, Psychologist, Patient, Connection).
    - `infra/`: Infraestrutura (Segurança, Configurações de Exceções, Swagger).
    - `mapper/`: Interfaces MapStruct para conversão Entidade <-> DTO.
    - `model/`:
        - `entities/`: Entidades JPA.
        - `dtos/`: Objetos de Transferência de Dados.
        - `enums/`: Enumerações (Roles, Status, etc.).
    - `repositories/`: Interfaces de acesso ao banco.
    - `services/`: Lógica de negócio (Interfaces e Implementações).

## 🔐 Segurança e Autenticação
- **Autenticação:** Baseada em JWT (Stateless). O token contém o e-mail (subject) e o papel (role) do usuário.
- **Roles:** `ADMIN`, `PSYCHOLOGIST`, `PATIENT`.
- **Proteção de Rotas:** 
    - `/auth/**`: Público.
    - `/admin/**`: Apenas `ROLE_ADMIN`.
    - Outras: Autenticadas.
- **Privacidade:** CPFs são mascarados para admins e nunca expostos publicamente. Senhas e campos de segurança interna nunca são serializados.

## 🏗 Padrões de API
- **Paginação:** Todos os endpoints de listagem/busca utilizam `org.springframework.data.domain.Page`.
- **Respostas de Erro:** Padronizadas via `GlobalExceptionHandler` usando o objeto `ErrorResponse`:
    - `status`: Código HTTP.
    - `message`: Mensagem amigável.
    - `timestamp`: Data/hora do erro.
    - `errors`: (Opcional) Mapa de erros de validação de campos.
- **Verbos REST:** 
    - `POST` para criação (201 Created).
    - `PUT`/`PATCH` para atualizações.
    - `DELETE` para remoção (204 No Content).

## 🤝 Sistema de Conexões
Gerencia o vínculo profissional entre Pacientes e Psicólogos.
- **Status:** `PENDING`, `ACCEPTED`, `REJECTED`, `REMOVED`.
- **Regras de Negócio:**
    - Administradores não participam de conexões.
    - Não é permitido conectar-se a si mesmo.
    - Apenas o destinatário original pode aceitar/rejeitar solicitações.

## 🚀 Performance e Escalabilidade
- **N+1 Prevention:** Uso de `@EntityGraph` para relacionamentos OneToOne/ManyToOne.
- **Batch Fetching:** Uso de `@BatchSize` em coleções ManyToMany (ex: Especialidades) para evitar carregamento excessivo em memória durante a paginação.
- **Transacionalidade:** Métodos de leitura marcados com `@Transactional(readOnly = true)`.

## 🛠 Diretrizes de Desenvolvimento
1. **DTOs:** Sempre use DTOs para entrada e saída de dados. Nunca exponha entidades JPA.
2. **Validação:** Use anotações do Bean Validation nos DTOs de Request.
3. **Mappers:** Utilize MapStruct para todas as conversões de tipos.
4. **Logs:** Utilize `log.info`, `log.warn` ou `log.error` para eventos importantes, evitando dados sensíveis.

## 📖 Links Úteis
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **Repositório Frontend:** [Psiconet-Frontend](https://github.com/jonathaneamorim/Psiconet-Frontend)
