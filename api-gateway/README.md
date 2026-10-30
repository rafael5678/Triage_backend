# TriageIA — API Gateway (`api-gateway`)

**Punto único de entrada y enrutador perimetral del Sistema de Triaje Hospitalario Inteligente.**

---

## 1. Responsabilidad y Alcance
El **API Gateway** es el componente frontal del backend de TriageIA. Proporciona:
- Enrutamiento inteligente hacia los cuatro microservicios de dominio (`ms-pacientes-auth`, `ms-evaluacion-triaje`, `ms-cola-espera`, `ms-ia-recomendacion`).
- Discriminación de rutas por método HTTP (resolviendo conflictos como `POST /api/v1/triage/patients` para registro vs `GET /api/v1/triage/patients` para la cola).
- Gestión centralizada de CORS para aplicaciones web (React en Vercel y desarrollo local).
- Inyección de identificadores de correlación (`X-Correlation-Id`) en cada solicitud para trazabilidad y observabilidad distribuida.
- Endpoint de verificación de salud perimetral (`/api/gateway/status` y `/actuator/health`).

---

## 2. Tecnologías
- **Java 17 / 21 LTS**
- **Spring Boot 3.3.4**
- **Spring Cloud Gateway 2023.0.3** (Motor reactivo basado en Project Reactor y Netty)
- **Spring Boot Actuator**
- **Docker & Docker Compose**

---

## 3. Matriz de Enrutamiento

| Método | Ruta Externa (Gateway) | Servicio Destino | Endpoint Interno |
|---|---|---|---|
| `POST` | `/api/v1/auth/**` | `ms-pacientes-auth` (8081) | `/api/v1/auth/**` |
| `GET/POST/PATCH` | `/api/v1/admin/users/**` | `ms-pacientes-auth` (8081) | `/api/v1/admin/users/**` |
| `POST` | `/api/v1/triage/patients` | `ms-pacientes-auth` (8081) | `/api/v1/triage/patients` |
| `GET` | `/api/v1/triage/patients/*/history` | `ms-evaluacion-triaje` (8082) | `/api/v1/triage/patients/{id}/history` |
| `POST` | `/api/v1/triage/evaluations/**` | `ms-evaluacion-triaje` (8082) | `/api/v1/triage/evaluations/**` |
| `POST` | `/api/v1/triage/analyze` | `ms-evaluacion-triaje` (8082) | `/api/v1/triage/analyze` |
| `GET` | `/api/v1/triage/patients` | `ms-cola-espera` (8083) | `/api/v1/triage/patients` |
| `PATCH/POST` | `/api/v1/triage/care/**` | `ms-cola-espera` (8083) | `/api/v1/triage/care/**` |
| `GET` | `/api/v1/triage/events` (SSE) | `ms-cola-espera` (8083) | `/api/v1/triage/events` |
| `GET` | `/api/v1/triage/stats` | `ms-cola-espera` (8083) | `/api/v1/triage/stats` |
| `GET` | `/api/v1/triage/board` | `ms-cola-espera` (8083) | `/api/v1/triage/board` |
| `GET` | `/api/v1/triage/health` | `ms-cola-espera` (8083) | `/api/v1/triage/health` |
| `POST` | `/api/v1/ia/**` | `ms-ia-recomendacion` (8084) | `/api/v1/ia/**` |
| `GET` | `/api/v1/catalog/**` | `ms-ia-recomendacion` (8084) | `/api/v1/catalog/**` |
| `GET/POST` | `/api/v1/admin/symptoms/**` | `ms-ia-recomendacion` (8084) | `/api/v1/admin/symptoms/**` |

---

## 4. Variables de Entorno

| Variable | Valor por Defecto | Descripción |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP en el que escucha el Gateway. |
| `MS_PACIENTES_URL` | `http://localhost:8081` | Dirección interna del servicio de pacientes y auth. |
| `MS_EVALUACION_URL` | `http://localhost:8082` | Dirección interna del servicio de evaluación clínica. |
| `MS_COLA_URL` | `http://localhost:8083` | Dirección interna del servicio de cola y turnos. |
| `MS_IA_URL` | `http://localhost:8084` | Dirección interna del servicio de IA y catálogo. |
| `CORS_ORIGINS` | `http://localhost:5173,...` | Orígenes permitidos para peticiones cruzadas. |

---

## 5. Ejecución

### Ejecución Local con Maven:
```bash
mvn -pl api-gateway -am spring-boot:run
```

### Ejecución con Docker:
```bash
docker build -t triage-api-gateway -f api-gateway/Dockerfile .
docker run -p 8080:8080 triage-api-gateway
```

