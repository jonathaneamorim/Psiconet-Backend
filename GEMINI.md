# Project Psiconet - Backend Documentation

This document serves as the primary source of truth for AI agents and developers working on the Psiconet Backend.

## 🚀 Project Overview
Psiconet is a platform designed to connect psychologists and patients, facilitating appointment scheduling, medical record management, and secure communication. The backend is built using **Spring Boot 3.3.5** and follows a layered architecture.

## 🛠 Tech Stack
- **Language:** Java 17
- **Framework:** Spring Boot 3.3.5
- **Security:** Spring Security with JWT (Stateless)
- **Database:** PostgreSQL
- **Persistence:** Spring Data JPA
- **Mapping:** MapStruct & Lombok
- **Documentation:** SpringDoc OpenAPI (Swagger)
- **Validation:** Hibernate Validator (Bean Validation)

## 📁 Project Structure
The project follows a standard Spring Boot structure:
- `src/main/java/com/psiconet/`
    - `controllers/`: REST Endpoints (Auth, User, Admin, Psychologist).
    - `infra/`: Infrastructure concerns (Security, Exceptions, Config, Swagger).
    - `mapper/`: MapStruct interfaces for DTO <-> Entity conversion.
    - `model/`: Data definitions.
        - `entities/`: JPA Entities (Access, Clinical, Document, Financial, Profile).
        - `dtos/`: Data Transfer Objects (Auth, Admin, Profile, Access).
        - `enums/`: Enumerations (Roles, Statuses).
    - `repositories/`: Spring Data JPA repositories.
    - `services/`: Business logic.
        - `interfaces/`: Service definitions.
        - `implement/`: Service implementations.
- `src/main/resources/`: Configuration files (`application.properties`).
- `src/test/java/`: Unit and integration tests.

## 🔐 Security & Authentication
- **Authentication:** JWT-based stateless authentication.
- **Roles:** Defined in `RoleEnum` (e.g., `ADMIN`, `PSYCHOLOGIST`, `PATIENT`).
- **Authorization:** Handled in `SecurityConfig.java` using `SecurityFilter`.
- **Registration Flow:**
    - `/auth/register/patient`: Registers a patient.
    - `/auth/register/psychologist`: Registers a psychologist.
- **Login:** `/auth/login` returns a JWT token.

## 🏗 Core Entities & Data Model
The project uses a relational database (PostgreSQL) with the following key entities and relationships:

- **User (`usuario`):**
    - Base entity for authentication and identity.
    - Fields: `id`, `email`, `cpf`, `password`, `role`, `status`, `fullName`, `phone`, `photoUrl`, `location`.
    - One-to-One relationship with `Patient` or `Psychologist`.

- **Patient (`paciente`):**
    - Extends the user as a patient profile.
    - Fields: `id`, `usuario_id`.

- **Psychologist (`psicologo`):**
    - Extends the user as a psychologist profile.
    - Fields: `id`, `usuario_id`, `crp`, `experienceTime`, `description`.
    - Many-to-Many relationship with `Specialty`.

- **Specialty (`especialidade`):**
    - Areas of expertise (e.g., "Cognitive Behavioral Therapy").
    - Joined via `especialidade_psicologo`.

- **Appointment (`agendamento`):**
    - Link between a `Patient` and a `Psychologist` for a session.

- **Connection (`conexao`):**
    - Status-based link between a Patient and a Psychologist (e.g., PENDING, ACCEPTED).

## 🚦 API Endpoints (Summary)
- **Auth:** `/auth/**` (Public)
    - `POST /auth/register/patient`
    - `POST /auth/register/psychologist`
    - `POST /auth/login`
- **Psychologists:** `/psychologists/**`
    - `GET /psychologists/search?name={name}&crp={crp}`
- **Users:** `/users/**`
- **Admin:** `/admin/**` (Requires `ROLE_ADMIN`)
    - `GET /admin/users`: Paginated list of users. Supports `page`, `size`, and `sort` (default: `fullName`).
    - `PATCH /admin/users/{id}/status`: Updates user status only.
    - `PUT /admin/users/{id}`: Updates full name, phone, and status.

## 🛡️ Admin Module & Security
The Admin module provides tools for platform management, strictly protected by `ROLE_ADMIN`.

### Data Exposure & Privacy
- **Masked CPF:** For privacy, CPFs in admin listings are masked as `123.***.***-45`.
- **Sensitive Data:** Passwords, full birth dates, and full CPFs are never exposed in administrative DTOs.
- **Minimal Exposure:** DTOs are tailored to show only necessary information for management.

### Editable vs. Protected Fields
- **Editable:** `fullName`, `phone`, `status`.
- **Protected:** `id`, `email`, `cpf`, `role`, `createdAt`. These fields cannot be modified via administrative endpoints to maintain data integrity.

### Pagination
Administrative lists use Spring Data `Page` response, including metadata like `totalElements`, `totalPages`, and `content`.

### Future Audit Logging
The structure is prepared to integrate with the `ChangeLog` entity for tracking administrative actions. Service methods are transactional and centralized to facilitate hook injection for logging.

## ⚠️ Error Handling
- Handled by `GlobalExceptionHandler`.
- Common responses:
    - `400 Bad Request`: Validation errors or business rule violations.
    - `401 Unauthorized`: Missing or invalid JWT.
    - `404 Not Found`: Entity not found.
    - `409 Conflict`: Duplicate data (e.g., email/CPF already exists).

## 🛠 Development Guidelines
- **Naming:** Follow standard Java CamelCase.
- **Entities:** Always use Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`.
- **DTOs:** Use DTOs for all request/response bodies.
- **Mappers:** Use MapStruct for conversions.
- **Validation:** Use Bean Validation annotations (`@NotBlank`, `@Email`, etc.).
- **Tests:** Add unit tests for business logic in services and integration tests for controllers.

## 📖 Useful Links
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **Frontend Repo:** [Psiconet-Frontend](https://github.com/jonathaneamorim/Psiconet-Frontend)
