# TriageIA — MS Pacientes y Autenticación (`ms-pacientes-auth`)

**Microservicio de gestión de historias clínicas, registro de pacientes y autenticación de personal hospitalario.**

---

## 1. Responsabilidad y Alcance
- **Dueño de los datos:** Tablas `pacientes` y `app_users`.
- Registro y consulta de información demográfica, antecedentes clínicos y datos obstétricos.
- Autenticación mediante credenciales con contraseñas cifradas en BCrypt.
- Proveedor del servidor gRPC `PatientLookup` (`GetPatient`, `GetPatients`) para que otros microservicios obtengan instantáneamente la ficha clínica sin acoplamiento a base de datos.

---

## 2. Tecnologías
- **Java 17 / 21 LTS**
- **Spring Boot 3.3.4** (Jetty con HTTP/2 h2c)
- **Spring Data JPA / Hibernate**
- **PostgreSQL 16**
- **Spring Security Crypto (BCrypt)**
- **gRPC Java + Protocol Buffers (`grpc-servlet-jakarta`)**

---

## 3. Endpoints REST

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Validación de credenciales y retorno de sesión de usuario. |
| `POST` | `/api/v1/triage/patients` | Registro de nuevo paciente para triaje. |
| `GET` | `/api/v1/admin/users` | Listado de usuarios del sistema (personal médico/admin). |
| `POST` | `/api/v1/admin/users` | Creación de nuevo usuario hospitalario. |
| `PATCH`| `/api/v1/admin/users/{id}/active` | Activación o suspensión de usuario. |

---

## 4. Contrato gRPC Expuesto
- **Servicio:** `hospital.triage.v1.PatientLookup`
- **Métodos:**
  - `rpc GetPatient (PatientId) returns (PatientView)`
  - `rpc GetPatients (PatientIds) returns (PatientList)`

---

## 5. Variables de Entorno

| Variable | Valor por Defecto | Descripción |
|---|---|---|
| `PORT` | `8081` | Puerto HTTP/2 del microservicio. |
| `DB_URL` | `jdbc:postgresql://localhost:5434/hospital_triaje` | URL de conexión JDBC a PostgreSQL. |
| `DB_USER` | `triage` | Usuario de base de datos. |
| `DB_PASSWORD` | `triage` | Contraseña de base de datos. |
| `BOOTSTRAP_ADMIN_USERNAME` | `admin` | Usuario inicial para el administrador. |
| `BOOTSTRAP_ADMIN_PASSWORD` | `UrgenciasAdmin26` | Contraseña inicial del administrador. |

