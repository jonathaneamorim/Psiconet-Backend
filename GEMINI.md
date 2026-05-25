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
- **Status Relativo:** `NONE`, `PENDING_SENT`, `PENDING_RECEIVED`, `CONNECTED`.
- **Regras de Negócio:**
    - Administradores não participam de conexões.
    - Não é permitido conectar-se a si mesmo.
    - Apenas o destinatário original pode aceitar/rejeitar solicitações.

## 🚀 Performance e Escalabilidade
- **N+1 Prevention:** Uso de `@EntityGraph` para relacionamentos OneToOne/ManyToOne.
- **Batch Fetching:** Uso de `@BatchSize` em coleções ManyToMany (ex: Especialidades) para evitar carregamento excessivo em memória durante a paginação.

---

## 🚦 Catálogo de Endpoints

### 1. Autenticação (`/auth`)

#### **POST /auth/register/patient**
- **Descrição:** Cadastro de novo paciente.
- **Request Body (`PatientRegisterRequest`):**
    - `email`, `fullName`, `cpf`, `birthDate`, `password`.
- **Response:** `201 Created`.

#### **POST /auth/register/psychologist**
- **Descrição:** Cadastro de novo psicólogo.
- **Request Body (`PsychologistRegisterRequest`):**
    - `email`, `fullName`, `cpf`, `birthDate`, `password`, `crp`.
- **Response:** `201 Created`.

#### **POST /auth/login**
- **Descrição:** Autenticação e geração de token.
- **Request Body (`AuthenticationDTO`):**
    - `email`, `password`.
- **Response (`LoginResponseDTO`):**
    - `token`: String JWT.

---

### 2. Perfis e Busca (`/psychologists`, `/patients`, `/users`)

#### **GET /users/me**
- **Descrição:** Retorna os dados completos do perfil do usuário logado.
- **Response:** `PatientMeDTO` ou `PsychologistMeDTO`.

#### **GET /psychologists/{id}**
- **Descrição:** Perfil público de um psicólogo com contexto de conexão.
- **Response (`PsychologistProfileDTO`):**
    - `id`, `fullName`, `photoUrl`, `crp`, `specialties`, `experienceTime`, `description`, `city`, `state`, `connectionStatus`, `connectionId`.

#### **GET /psychologists/search**
- **Descrição:** Busca paginada de psicólogos.
- **Query Params:** `name` (opcional), `crp` (opcional), `page`, `size`.
- **Response:** `Page<SearchPsychologistDTO>`.

#### **GET /patients/{id}**
- **Descrição:** Perfil público de um paciente com contexto de conexão.
- **Response (`PatientProfileDTO`):**
    - `id`, `fullName`, `photoUrl`, `city`, `state`, `connectionStatus`, `connectionId`.

#### **GET /patients/search**
- **Descrição:** Busca paginada de pacientes.
- **Query Params:** `name` (obrigatório), `page`, `size`.
- **Response:** `Page<SearchPatientDTO>`.

---

### 3. Conexões (`/connections`)

#### **POST /connections/{targetUserId}**
- **Descrição:** Envia solicitação de conexão.
- **Response:** `200 OK`.

#### **PATCH /connections/{id}/accept**
- **Descrição:** Aceita solicitação recebida.
- **Response:** `204 No Content`.

#### **PATCH /connections/{id}/reject**
- **Descrição:** Rejeita solicitação recebida.
- **Response:** `204 No Content`.

#### **DELETE /connections/{id}**
- **Descrição:** Cancela solicitação ou remove conexão ativa.
- **Response:** `204 No Content`.

#### **GET /connections**
- **Descrição:** Lista conexões ativas do usuário.
- **Response:** `Page<ActiveConnectionDTO>`.

#### **GET /connections/pending**
- **Descrição:** Lista solicitações recebidas pendentes.
- **Response:** `Page<ConnectionDTO>`.

---

### 4. Administração (`/admin`) - Exige `ROLE_ADMIN`

#### **GET /admin/users**
- **Descrição:** Lista todos os usuários do sistema.
- **Response:** `Page<UsersToListAdminDTO>`.

#### **PATCH /admin/users/{id}/status**
- **Descrição:** Altera status de um usuário (`ACTIVE`, `INACTIVE`, `BLOCKED`, etc.).
- **Response:** `204 No Content`.

#### **PUT /admin/users/{id}**
- **Descrição:** Atualização administrativa de dados do usuário.
- **Response:** `204 No Content`.

---

## 🛠 Diretrizes de Desenvolvimento
1. **DTOs:** Sempre use DTOs. Nunca exponha entidades JPA.
2. **Validação:** Use Bean Validation nos DTOs de Request.
3. **Mappers:** Utilize MapStruct para todas as conversões.
4. **Relacionamento:** Inclua `connectionStatus` e `connectionId` em perfis públicos.
5. **Erros:** Trate exceções de negócio com `BusinessException`.

## 📖 Links Úteis
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **Repositório Frontend:** [Psiconet-Frontend](https://github.com/jonathaneamorim/Psiconet-Frontend)
