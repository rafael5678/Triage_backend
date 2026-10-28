# Triage de urgencias

Cuatro microservicios Spring Boot 3.3 (Java 17). El frontend entra por REST y los servicios se llaman por gRPC, en el mismo puerto de cada uno.

- `ms-pacientes-auth` — pacientes y login, 8081
- `ms-evaluacion-triaje` — signos vitales y score, 8082
- `ms-cola-espera` — orden de la sala, 8083
- `ms-ia-recomendacion` — Gemini y catálogo, 8084

Detalle en `docs/ARCHITECTURE.md`. Despliegue en `docs/DEPLOYMENT_AZURE.md` (o `docs/DEPLOYMENT_CLOUD_RUN.md`).

Salud: `GET /api/v1/health` en pacientes y `GET /api/v1/triage/health` en evaluación, cola e IA.
