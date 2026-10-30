# TriageIA — MS Cola de Espera (`ms-cola-espera`)

**Microservicio de gestión de sala de espera, ordenamiento dinámico de prioridades, avisos por WhatsApp y eventos en tiempo real (SSE).**

---

## 1. Responsabilidad y Alcance
- **Desacoplado de la persistencia directa:** No posee conexión a base de datos. Orquesta en memoria el orden de la sala de espera y persiste cambios de estado mediante gRPC en `ms-evaluacion-triaje`.
- Algoritmo de ordenamiento por riesgo inmediato: Mayor puntuación clínica primero; en caso de empate, menor tiempo de llegada.
- Emisión de eventos en vivo mediante Server-Sent Events (`/api/v1/triage/events`) para actualización automática de la pantalla de sala sin recargar página.
- Cálculo de tiempos de espera estimados y percentiles de atención.
- Integración con Meta WhatsApp Cloud API para notificación de proximidad de turno.

---

## 2. Tecnologías
- **Java 17 / 21 LTS**
- **Spring Boot 3.3.4** (Tomcat / Spring Web)
- **Server-Sent Events (SSE)**
- **gRPC Java Client (`net.devh:grpc-client-spring-boot-starter`)**
- **Meta WhatsApp Cloud API Client**

---

## 3. Endpoints REST

| Método | Endpoint | Descripción |
|---|---|---|
| `GET`   | `/api/v1/triage/patients` | Lista ordenada y priorizada de pacientes en espera con tiempos de espera calculados. |
| `PATCH` | `/api/v1/triage/care/{id}` | Inicia atención médica del paciente (cambia a `EN_ATENCION`). |
| `PATCH` | `/api/v1/triage/care/{id}/status` | Finaliza atención (ej. `ATENDIDO` o `CANCELADO`). |
| `PATCH` | `/api/v1/triage/care/{id}/presence` | Registra presencia o ausencia física en sala. |
| `POST`  | `/api/v1/triage/care/{id}/notified` | Marca aviso manual o automático al paciente. |
| `GET`   | `/api/v1/triage/events` | Stream SSE de eventos en vivo para clientes web y pantallas públicas. |
| `GET`   | `/api/v1/triage/stats` | Estadísticas del turno (tiempos promedio, pacientes atendidos por nivel). |
| `GET`   | `/api/v1/triage/board` | Información simplificada y anonimizada para tablero público en sala. |
| `GET`   | `/api/v1/triage/health` | Diagnóstico de salud del servicio. |

---

## 4. Contratos gRPC Consumidos
- **Cliente:** `hospital.triage.v1.EvaluationAccess` (conectado a `ms-evaluacion-triaje`)

---

## 5. Variables de Entorno

| Variable | Valor por Defecto | Descripción |
|---|---|---|
| `PORT` | `8083` | Puerto HTTP del microservicio. |
| `GRPC_CLIENT_EVALUACION` | `static://localhost:8082` | Dirección gRPC de `ms-evaluacion-triaje`. |
| `WHATSAPP_TOKEN` | *(Opcional)* | Token de acceso de WhatsApp Cloud API. |
| `WHATSAPP_PHONE_ID` | *(Opcional)* | ID del número telefónico de WhatsApp en Meta Developer. |

