# Arquitectura de microservicios

Cuatro procesos Spring Boot. El navegador habla REST. Los servicios hablan gRPC entre sí.

REST y gRPC comparten el puerto de cada servicio: pacientes, IA y evaluación corren Jetty con HTTP/2 en texto plano (h2c). Así funciona igual en local y en Cloud Run, que abre un solo puerto por servicio.

| Servicio | Puerto local | Recibe gRPC | Dueño de |
|---|---|---|---|
| ms-pacientes-auth | 8081 | sí | `pacientes`, `app_users` |
| ms-evaluacion-triaje | 8082 | sí | `signos_vitales`, `evaluaciones_triage` |
| ms-cola-espera | 8083 | no, solo llama | no persiste; ordena la cola |
| ms-ia-recomendacion | 8084 | sí | `symptoms` y la llamada a Gemini |

## Llamadas internas

- Evaluación pide la ficha a pacientes (`PatientLookup`) y el criterio clínico a IA (`ClinicalAi`).
- La cola pide la lista y el cambio de estado a evaluación (`EvaluationAccess`), y le devuelve las posiciones.
- Si IA no responde, la evaluación sigue con el protocolo.

Los contratos están en `triage-grpc-contracts/src/main/proto/triage.proto`.

## Arranque local

Con Postgres en `localhost:5434`:

```bash
mvn -pl ms-pacientes-auth -am spring-boot:run
mvn -pl ms-ia-recomendacion -am spring-boot:run
mvn -pl ms-evaluacion-triaje -am spring-boot:run
mvn -pl ms-cola-espera -am spring-boot:run
```

O todo junto: `docker compose up --build`.

El frontend usa `VITE_AUTH_URL`, `VITE_EVAL_URL`, `VITE_QUEUE_URL` y `VITE_AI_URL`.

Despliegue: `docs/DEPLOYMENT_AZURE.md` (Azure Container Apps) o `docs/DEPLOYMENT_CLOUD_RUN.md`.

`legacy-monolith` queda como referencia del proceso único en el puerto 8080. La aplicación ya no entra por ahí.
