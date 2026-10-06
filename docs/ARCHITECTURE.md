# Arquitectura Hexagonal - Microservicio Triage Backend

El microservicio está construido sobre **Spring Boot 3** bajo principios de diseño guiado por el dominio (DDD) y arquitectura hexagonal.

## Capas del Sistema
1. **API / Presentación (`api/`)**: Controladores REST y manejador global de excepciones.
2. **Aplicación (`application/`)**: DTOs de transferencia y servicios orquestadores (`TriageService`, `ColaAtencionService`).
3. **Dominio (`domain/`)**: Entidades centrales (`Paciente`, `SignosVitales`, `EvaluacionTriage`), enums y excepciones de negocio.
4. **Infraestructura (`infrastructure/`)**: Configuración de persistencia Spring Data JPA, CORS y pools HikariCP.
