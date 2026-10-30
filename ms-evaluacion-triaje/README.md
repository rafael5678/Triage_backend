# TriageIA — MS Evaluación de Triaje (`ms-evaluacion-triaje`)

**Microservicio de evaluación clínica, captura de signos vitales, reglas clínicas de gravedad y cálculo NEWS2.**

---

## 1. Responsabilidad y Alcance
- **Dueño de los datos:** Tablas `signos_vitales` y `evaluaciones_triage`.
- Implementación de la matriz clínica de riesgo inmediato:
  - Puntuación NEWS2 (Adultos).
  - Rangos de signos vitales por edad (`PediatricBand` - Lactantes y niños).
  - Reglas obstétricas (Preeclampsia severa, eclampsia, sangrado gestacional).
  - Umbrales de signos vitales extremos (piso de gravedad $\ge 86$).
  - Asignación de áreas de urgencias (`DestinationService`).
- Fusión híbrida con sugerencias de IA provenientes de `ms-ia-recomendacion`.
- Servidor gRPC `EvaluationAccess` (`ListByStatus`, `ListAll`, `UpdateCareStatus`, `ApplyPositions`, `MarkNotified`, `SetPresence`).
- Cliente gRPC hacia `ms-pacientes-auth` y `ms-ia-recomendacion`.

---

## 2. Tecnologías
- **Java 17 / 21 LTS**
- **Spring Boot 3.3.4** (Jetty HTTP/2 h2c)
- **Spring Data JPA / Hibernate**
- **PostgreSQL 16**
- **gRPC Java + Protocol Buffers + NetDevh gRPC Client Starter**

---

## 3. Endpoints REST

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/v1/triage/evaluations` | Registro de signos vitales y cálculo de triaje para un paciente registrado. |
| `POST` | `/api/v1/triage/analyze` | Análisis instantáneo de triaje (calcula score, nivel, destino y recomendaciones). |
| `PUT`  | `/api/v1/triage/evaluations/{id}/reassess` | Reevaluación de signos vitales de un paciente en espera. |
| `GET`  | `/api/v1/triage/patients/{id}/history` | Historial cronológico de mediciones y evoluciones del paciente. |

---

## 4. Contratos gRPC
- **Servidor Expuesto:** `hospital.triage.v1.EvaluationAccess`
- **Clientes Consumidos:**
  - `hospital.triage.v1.PatientLookup` (en `ms-pacientes-auth`)
  - `hospital.triage.v1.ClinicalAi` (en `ms-ia-recomendacion`)

---

## 5. Variables de Entorno

| Variable | Valor por Defecto | Descripción |
|---|---|---|
| `PORT` | `8082` | Puerto HTTP/2 del microservicio. |
| `DB_URL` | `jdbc:postgresql://localhost:5434/hospital_triaje` | URL de conexión JDBC a PostgreSQL. |
| `GRPC_CLIENT_PACIENTES` | `static://localhost:8081` | Dirección del cliente gRPC de Pacientes. |
| `GRPC_CLIENT_IA` | `static://localhost:8084` | Dirección del cliente gRPC de IA. |
| `GRPC_NEGOTIATION` | `plaintext` | Negociación gRPC (`plaintext` en local / red interna, `tls` en transporte seguro). |

