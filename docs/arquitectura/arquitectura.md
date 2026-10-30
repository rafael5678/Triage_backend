# Arquitectura Global de Microservicios — TriageIA

## 1. Visión General
**TriageIA** es un sistema hospitalario distribuido para la recepción, evaluación clínica, estratificación del riesgo mediante reglas de triaje (NEWS2, rangos pediátricos, códigos obstétricos) y gestión de salas de espera con apoyo opcional de Inteligencia Artificial (Google Gemini).

El sistema sigue una arquitectura orientada a microservicios desacoplados con:
- **API Gateway:** Punto único de entrada exterior para clientes web (React en Vercel) y observabilidad.
- **Microservicios de Dominio:** Procesos autónomos en Spring Boot ejecutados en contenedores Docker independientes.
- **Comunicación Interna:** gRPC con Protocol Buffers sobre HTTP/2 para máxima velocidad y tipado estricto.
- **Comunicación Externa:** REST API sobre JSON y Server-Sent Events (SSE) para actualizaciones en vivo.
- **Persistencia:** PostgreSQL compartido con segregación lógica de propiedad de tablas por microservicio.
- **Despliegue Cloud:** Microsoft Azure (Azure Container Apps, Azure Container Registry, Azure Key Vault).

---

## 2. Diagrama de Arquitectura Global

```mermaid
flowchart TD
    subgraph Clientes["Capa de Presentación"]
        WEB["Frontend Web (React / Vite en Vercel)"]
        BOARD["Tablero de Sala (Pantalla de Urgencias)"]
    end

    subgraph Perimetro["Perímetro y Enrutamiento"]
        GW["API Gateway (Spring Cloud Gateway :8080)"]
    end

    subgraph Cluster["Red Interna de Microservicios (Azure Container Apps)"]
        MS_AUTH["ms-pacientes-auth (:8081)\n• Registro de Pacientes\n• Autenticación Personal"]
        MS_EVAL["ms-evaluacion-triaje (:8082)\n• Signos Vitales\n• NEWS2 & Pediatría\n• Destino Hospitalario"]
        MS_COLA["ms-cola-espera (:8083)\n• Orden Dinámico de Sala\n• Eventos SSE\n• Avisos WhatsApp"]
        MS_IA["ms-ia-recomendacion (:8084)\n• Gemini AI Analysis\n• Catálogo de Síntomas\n• Control de Cuotas"]
    end

    subgraph Externos["Servicios Externos"]
        GEMINI["Google Gemini API (Generative AI)"]
        META["Meta WhatsApp Cloud API"]
    end

    subgraph Persistencia["Capa de Datos"]
        DB[("PostgreSQL 16\nhospital_triaje")]
    end

    WEB -->|HTTPS / REST| GW
    BOARD -->|HTTPS / SSE| GW

    GW -->|/api/v1/auth, /api/v1/triage/patients POST| MS_AUTH
    GW -->|/api/v1/triage/evaluations, /analyze| MS_EVAL
    GW -->|/api/v1/triage/patients GET, /care, /events| MS_COLA
    GW -->|/api/v1/ia, /catalog| MS_IA

    MS_EVAL -.->|gRPC PatientLookup| MS_AUTH
    MS_EVAL -.->|gRPC ClinicalAi| MS_IA
    MS_COLA -.->|gRPC EvaluationAccess| MS_EVAL

    MS_IA -->|REST HTTPS| GEMINI
    MS_COLA -->|REST HTTPS| META

    MS_AUTH -->|JPA / Tablas: pacientes, app_users| DB
    MS_EVAL -->|JPA / Tablas: signos_vitales, evaluaciones| DB
    MS_IA -->|JPA & JDBC / Tablas: symptoms, uso_gemini| DB
```

---

## 3. Principios de Diseño
1. **Desacoplamiento Estricto:** Ningún microservicio comparte entidades JPA o tablas directamente con otro microservicio. La comunicación entre dominios se realiza exclusivamente a través de contratos gRPC o HTTP.
2. **Resiliencia y Fallback:** Si el microservicio de IA no responde o se agotan las cuotas de Gemini, `ms-evaluacion-triaje` continúa funcionando autónomamente con el protocolo clínico sin interrumpir la atención.
3. **Observabilidad Distribuida:** El API Gateway inyecta un encabezado `X-Correlation-Id` en cada solicitud para permitir el rastreo de punta a punta en los logs de Azure Monitor / Application Insights.

