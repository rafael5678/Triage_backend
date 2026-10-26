# Arquitectura de microservicios

Cuatro procesos Spring Boot. El navegador habla REST. Los servicios hablan gRPC entre sí, en texto plano dentro de la red local.

| Servicio | REST | gRPC | Dueño de |
|---|---|---|---|
| ms-pacientes-auth | 8081 | 9081 | `pacientes`, `app_users` |
| ms-evaluacion-triaje | 8082 | 9082 | `signos_vitales`, `evaluaciones_triage` |
| ms-cola-espera | 8083 | cliente | no persiste; ordena la cola |
| ms-ia-recomendacion | 8084 | 9084 | `symptoms` y la llamada a Gemini |

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

`legacy-monolith` queda como referencia del proceso único en el puerto 8080. La aplicación ya no entra por ahí.
