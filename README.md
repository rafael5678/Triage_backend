# Triage de urgencias

Cuatro microservicios Spring Boot 3.3 (Java 17). El frontend entra por REST y los servicios se llaman por gRPC.

- `ms-pacientes-auth` — pacientes y login, REST 8081, gRPC 9081
- `ms-evaluacion-triaje` — signos vitales y score, REST 8082, gRPC 9082
- `ms-cola-espera` — orden de la sala, REST 8083
- `ms-ia-recomendacion` — Gemini y catálogo, REST 8084, gRPC 9084

Detalle de puertos y arranque en `docs/ARCHITECTURE.md`.

Salud: `GET /api/v1/triage/health` en evaluación, cola e IA.
