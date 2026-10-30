# TriageIA — MS IA Recomendación (`ms-ia-recomendacion`)

**Microservicio de integración con Google Gemini, catálogo de síntomas y asistencia al triaje clínico.**

---

## 1. Responsabilidad y Alcance
- **Dueño de los datos:** Tablas `symptoms` y `uso_gemini`.
- Procesamiento de lenguaje natural sobre el relato del paciente (y análisis opcional de fotografía de lesiones).
- Extracción de datos clínicos sugeridos (síntomas, signos vitales estimados, comorbilidades).
- Explicación de campos clínicos (`/api/v1/ia/explain`).
- Control de consumo y cuotas de IA:
  - Cache en memoria de respuestas recientes.
  - Almacén en PostgreSQL (`uso_gemini`) para controlar el tope diario de llamadas.
  - Límite por minuto y reintento con modelo de respaldo.
  - Conmutación automática a modo protocolo si la API de IA no responde o se agota la cuota.
- Servidor gRPC `ClinicalAi` (`Assist`, `Health`, `SymptomSeverity`).

---

## 2. Tecnologías
- **Java 17 / 21 LTS**
- **Spring Boot 3.3.4** (Jetty HTTP/2 h2c)
- **Spring Data JPA & JdbcTemplate**
- **PostgreSQL 16**
- **Java 11+ HttpClient (REST client asíncrono para Google Gemini API)**
- **gRPC Java + Protocol Buffers (`grpc-servlet-jakarta`)**

---

## 3. Endpoints REST

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/v1/ia/intake` | Transforma el relato libre del paciente en campos estructurados de triaje. |
| `POST` | `/api/v1/ia/explain` | Genera una explicación clínica contextual sobre un signo vital o dato. |
| `GET`  | `/api/v1/catalog/symptoms` | Retorna el catálogo clínico activo de lesiones/síntomas con su severidad base. |
| `GET`  | `/api/v1/admin/symptoms` | Consulta administrativa de síntomas (incluyendo inactivos). |
| `POST` | `/api/v1/admin/symptoms` | Creación o actualización de síntomas en el catálogo. |

---

## 4. Contrato gRPC Expuesto
- **Servidor:** `hospital.triage.v1.ClinicalAi`
- **Métodos:**
  - `rpc Assist (AssistRequest) returns (AssistReply)`
  - `rpc Health (Empty) returns (AiHealth)`
  - `rpc SymptomSeverity (SymptomQuery) returns (SymptomSeverityReply)`

---

## 5. Variables de Entorno

| Variable | Valor por Defecto | Descripción |
|---|---|---|
| `PORT` | `8084` | Puerto HTTP/2 del microservicio. |
| `DB_URL` | `jdbc:postgresql://localhost:5434/hospital_triaje` | URL de conexión JDBC a PostgreSQL. |
| `GEMINI_API_KEY` | *(Vacio)* | Clave de API de Google Gemini (Google AI Studio). |
| `GEMINI_MODEL` | `gemini-2.0-flash,gemini-1.5-flash` | Modelos de Gemini a utilizar (con fallback). |
| `GEMINI_DAILY_LIMIT` | `150` | Máximo de llamadas reales a la API por día. |
| `GEMINI_PER_MINUTE_LIMIT` | `6` | Máximo de llamadas por minuto. |

