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

### Gerando segredos com `openssl rand`
Os segredos do `.env` devem ser gerados aleatoriamente (nunca reaproveite valores de exemplo ou de outro ambiente).
O comando `openssl rand -base64 N` gera **N bytes aleatórios** e os imprime em Base64:

| Variável | Comando | Observação |
|---|---|---|
| `JWT_SECRET` | `openssl rand -base64 48` | Mínimo de 32 caracteres; a API não sobe sem ele. |
| `CLINICAL_MASTER_KEYS` | `openssl rand -base64 32` | Precisa ter **exatamente 32 bytes** (AES-256) e o **prefixo da versão**. |
| `POSTGRES_PASSWORD` | `openssl rand -base64 24` | Troque também a senha no Postgres. |

A chave do prontuário usa o formato `versao:chave`. Para gerar e já adicionar ao `.env`:

```bash
echo "CLINICAL_MASTER_KEYS=v1:$(openssl rand -base64 32)" >> .env
echo "CLINICAL_ACTIVE_KEY_VERSION=v1" >> .env
```

Resultado esperado no `.env` (exemplo ilustrativo, **não use**):

```env
CLINICAL_MASTER_KEYS=v1:3q2+7w8yX0aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456=
CLINICAL_ACTIVE_KEY_VERSION=v1
```

Cuidados com a chave do prontuário:
- **Faça backup fora do banco** (ex.: gerenciador de senhas). Perder a chave significa perder o acesso aos dados clínicos.
- **Não altere nem apague** uma chave que já tenha sido usada. Para rotacionar, adicione uma nova versão mantendo a antiga
  e aponte a versão ativa para ela — os prontuários são re-cifrados automaticamente na inicialização:
  ```env
  CLINICAL_MASTER_KEYS=v1:<chave antiga>,v2:<chave nova gerada com openssl rand -base64 32>
  CLINICAL_ACTIVE_KEY_VERSION=v2
  ```
- Use uma chave diferente por ambiente (dev, homologação, produção) e nunca a versione nem compartilhe em chats.

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
Para detalhes técnicos mais profundos sobre a estrutura de arquivos e diretrizes de desenvolvimento, consulte o [CLAUDE.md](./CLAUDE.md) e as decisões em [docs/decisoes](./docs/decisoes).

---

## 🔗 Links
- [Frontend do Projeto](https://github.com/jonathaneamorim/Psiconet-Frontend)
