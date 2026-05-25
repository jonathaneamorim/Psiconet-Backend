# Psiconet - Módulo Backend

Este é o módulo backend do projeto **Psiconet**, uma plataforma para conexão entre psicólogos e pacientes.

## 🚀 Início Rápido

### Pré-requisitos
- Java 17
- Docker e Docker Compose
- Maven

### Execução Local
1. Clone o repositório.
2. Configure o arquivo `.env` (baseie-se no `.env.example`).
3. Execute via Docker:
   ```bash
   docker compose up -d --build
   ```
   Ou via Maven:
   ```bash
   ./mvnw spring-boot:run
   ```

### Documentação da API
Após iniciar o servidor, a documentação Swagger estará disponível em:
- [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## 🏗 Arquitetura e Estrutura

O projeto utiliza **Spring Boot 3.3.5** com uma arquitetura em camadas:

- **Controllers:** Endpoints REST.
- **Services:** Lógica de negócio e regras.
- **Repositories:** Acesso ao banco de dados (PostgreSQL).
- **Entities:** Modelagem de dados com JPA.
- **DTOs & Mappers:** Transferência de dados e conversões seguras (MapStruct).

### Principais Funcionalidades
- Autenticação e Autorização via **JWT**.
- Cadastro diferenciado para Pacientes e Psicólogos.
- Busca de psicólogos por nome ou CRP.
- Gestão de prontuários, agendamentos e pagamentos (em desenvolvimento).

---

## 🔐 Segurança
A segurança é implementada com **Spring Security**, utilizando tokens JWT para autenticação stateless. As permissões são baseadas em perfis: `ADMIN`, `PSYCHOLOGIST`, e `PATIENT`.

---

## 🛠 Tecnologias Utilizadas
- **Java 17**
- **Spring Boot 3** (Data JPA, Security, Web, Validation)
- **PostgreSQL**
- **JWT** (java-jwt)
- **Lombok** & **MapStruct**
- **SpringDoc OpenAPI** (Swagger)

---

## 📝 Contribuição
Para detalhes técnicos mais profundos sobre a estrutura de arquivos e diretrizes de desenvolvimento, consulte o arquivo [GEMINI.md](./GEMINI.md).

---

## 🔗 Links
- [Frontend do Projeto](https://github.com/jonathaneamorim/Psiconet-Frontend)
