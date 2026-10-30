# TriageIA — Sistema de Triaje Hospitalario Inteligente (Backend)

[![Java 17/21](https://img.shields.io/badge/Java-17%2F21%20LTS-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![gRPC](https://img.shields.io/badge/gRPC-1.68.2-blue.svg)](https://grpc.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![Azure](https://img.shields.io/badge/Azure-Container%20Apps-0078D4.svg)](https://azure.microsoft.com/)

Sistema hospitalario distribuido de misión crítica diseñado para la gestión de pacientes de urgencias, registro de signos vitales, estratificación automática del riesgo clínico con protocolos validados (**NEWS2**, rangos pediátricos, alertas obstétricas), administración inteligente de la sala de espera y asistencia diagnóstica contextual mediante Inteligencia Artificial (Google Gemini).

---

## 1. Arquitectura de Microservicios

El sistema implementa una arquitectura orientada a microservicios desacoplados:

```
Frontend Web (Vercel)
        │
        ▼ (HTTPS REST / SSE)
┌────────────────────────────────────────────────────────┐
│               API Gateway (:8080)                     │
│    Enrutamiento • CORS • Correlation IDs • Health      │
└────────┬──────────────┬──────────────┬─────────────────┘
         │              │              │
    (REST/gRPC)    (REST/gRPC)    (REST/gRPC)
         │              │              │
         ▼              ▼              ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ ms-pacientes-  │ │ ms-evaluacion- │ │ ms-cola-       │ │ ms-ia-         │
│ auth (:8081)   │ │ triaje (:8082) │ │ espera (:8083) │ │ recomendacion  │
│                │ │                │ │                │ │ (:8084)        │
│ • Pacientes    │ │ • Signos       │ │ • Turnos Sala  │ │ • Gemini LLM   │
│ • Login/Roles  │ │ • NEWS2/Reglas │ │ • SSE en Vivo  │ │ • Catálogo     │
│ • PatientLookup│ │ • Destino Hosp │ │ • WhatsApp     │ │ • Cuotas       │
└───────┬────────┘ └───────┬────────┘ └───────┬────────┘ └───────┬────────┘
        │                  │                  │                  │
        │                  └──── gRPC ────────┼──────────────────┘
        │                                     │
        ▼                                     ▼
┌────────────────────────────────────────────────────────┐
│            PostgreSQL 16 (hospital_triaje)             │
│   Segregación lógica de tablas por microservicio       │
└────────────────────────────────────────────────────────┘
```

---

## 2. Estructura de Módulos del Repositorio

```text
Triage_backend/
├── pom.xml                        # Parent Reactor POM (Orquestador Maven)
├── docker-compose.yml             # Orquestación local completa con Gateway
├── .env.example                   # Variables de entorno documentadas
├── api-gateway/                   # Microservicio 0: API Gateway (8080)
├── ms-pacientes-auth/             # Microservicio 1: Pacientes y Autenticación (8081)
├── ms-evaluacion-triaje/          # Microservicio 2: Signos Vitales y Triaje Clínico (8082)
├── ms-cola-espera/                # Microservicio 3: Gestión de Cola y Turnos (8083)
├── ms-ia-recomendacion/           # Microservicio 4: Recomendación con IA y Catálogo (8084)
├── triage-common-contracts/       # Contratos compartidos (DTOs, Enums, Excepciones)
├── triage-grpc-contracts/         # Contratos Protocol Buffers y stubs gRPC
├── docs/                          # Documentación técnica categorizada y ADRs
│   ├── arquitectura/              # Diagramas globales, contextos DDD y ADRs (1 a 7)
│   ├── api/                       # Especificación de endpoints REST
│   ├── base-datos/                # Modelo relacional y esquemas
│   ├── seguridad/                 # Matriz de roles y seguridad perimetral
│   ├── testing/                   # Estrategia de pruebas automatizadas
│   ├── despliegue/                # Guías de Docker local
│   └── azure/                     # Arquitectura Cloud y estimación de costos
├── deploy/                        # Scripts de despliegue
│   └── azure/                     # Despliegue automatizado en Azure Container Apps
└── .github/
    └── workflows/                 # CI/CD (Validación de pruebas y despliegue)
```

---

## 3. Matriz de Puertos y Comunicación

| Módulo | Puerto | Tipo de Tráfico | Protocolo Interno | Base de Datos (Dueño de) |
|---|---|---|---|---|
| `api-gateway` | **8080** | REST / SSE Público | HTTP / Netty | *(Sin persistencia)* |
| `ms-pacientes-auth` | **8081** | REST & gRPC Servidor | HTTP/2 h2c | `pacientes`, `app_users` |
| `ms-evaluacion-triaje` | **8082** | REST & gRPC Servidor / Cliente | HTTP/2 h2c | `signos_vitales`, `evaluaciones_triage` |
| `ms-cola-espera` | **8083** | REST & gRPC Cliente | HTTP/1.1 & HTTP/2 | *(Sin DB directa; orquesta en memoria)* |
| `ms-ia-recomendacion` | **8084** | REST & gRPC Servidor | HTTP/2 h2c | `symptoms`, `uso_gemini` |
| `PostgreSQL` | **5434 (host)** | JDBC | TCP :5432 | `hospital_triaje` |

---

## 4. Ejecución del Proyecto

### Opción A: Levantar todo con Docker Compose (Recomendada)
```bash
docker compose up --build -d
```
El sistema estará listo en `http://localhost:8080`.
- Health check del Gateway: `curl http://localhost:8080/api/gateway/status`

### Opción B: Ejecución Local Modular con Maven
Con una instancia PostgreSQL activa en `localhost:5434`:
```bash
# Terminal 1: Iniciar microservicio de pacientes
mvn -pl ms-pacientes-auth -am spring-boot:run

# Terminal 2: Iniciar microservicio de IA
mvn -pl ms-ia-recomendacion -am spring-boot:run

# Terminal 3: Iniciar microservicio de evaluación clínica
mvn -pl ms-evaluacion-triaje -am spring-boot:run

# Terminal 4: Iniciar microservicio de cola de espera
mvn -pl ms-cola-espera -am spring-boot:run

# Terminal 5: Iniciar API Gateway
mvn -pl api-gateway -am spring-boot:run
```

---

## 5. Pruebas Automatizadas
La suite contiene **49 pruebas automatizadas** que protegen minuciosamente las reglas clínicas hospitalarias:
```bash
mvn test
```

---

## 6. Documentación Técnica y Decisiones Arquitectónicas (ADRs)
- [Arquitectura Global](docs/arquitectura/arquitectura.md)
- [Contextos Delimitados DDD](docs/arquitectura/contextos.md)
- [ADR-001: Microservicios](docs/arquitectura/adr/ADR-001-arquitectura-microservicios.md)
- [ADR-002: PostgreSQL Compartido](docs/arquitectura/adr/ADR-002-postgresql-compartido.md)
- [ADR-003: Comunicación gRPC](docs/arquitectura/adr/ADR-003-comunicacion-grpc-interna.md)
- [ADR-004: API Gateway](docs/arquitectura/adr/ADR-004-incorporacion-api-gateway.md)
- [ADR-005: Seguridad y Compatibilidad](docs/arquitectura/adr/ADR-005-seguridad-autenticacion.md)
- [ADR-006: Despliegue en Azure Container Apps](docs/arquitectura/adr/ADR-006-despliegue-azure-container-apps.md)
- [ADR-007: Gestión de Secretos en Key Vault](docs/arquitectura/adr/ADR-007-gestion-secretos-keyvault.md)
- [Especificación API REST](docs/api/especificacion-api.md)
- [Modelo de Datos Relacional](docs/base-datos/modelo-datos-y-esquemas.md)
- [Guía de Despliegue en Microsoft Azure](docs/azure/guia-despliegue-azure.md)
- [Estimación de Costos Cloud](docs/azure/estimacion-costos.md)
