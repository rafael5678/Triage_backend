# Emergency triage API

Spring Boot 3.3 (Java 17) service for ESI-style scoring, queue order, auth and Gemini assist.

## Layers

- `api` — REST controllers
- `application` — use cases, DTOs, services
- `domain` — entities, enums, exceptions
- `infrastructure` — JPA, config, bootstrap, outbound HTTP

Table and column names in Postgres stay as they were so existing data keeps working.

Health: `GET /api/v1/triage/health`
