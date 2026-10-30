# ADR-002: Base de Datos PostgreSQL Compartida con Segregación Lógica

## Estado
Aceptado

## Contexto
En arquitecturas puras de microservicios se suele recomendar una base de datos independiente por cada servicio ("Database per Service"). No obstante, en el contexto de este proyecto hospitalario y siguiendo expresamente las directrices del docente y las restricciones presupuestarias de infraestructura cloud, desplegar cuatro instancias administradas de PostgreSQL en Azure incrementaría sustancialmente los costos de operación sin justificación clínica en esta escala.

## Decisión
Se utiliza una única instancia de PostgreSQL 16 (`hospital_triaje`), aplicando el patrón **Shared Database with Logical Boundary**:
- Cada microservicio posee propiedad exclusiva de sus tablas.
- `ms-pacientes-auth`: `pacientes`, `app_users`.
- `ms-evaluacion-triaje`: `signos_vitales`, `evaluaciones_triage`.
- `ms-ia-recomendacion`: `symptoms`, `uso_gemini`.
- `ms-cola-espera`: No accede a base de datos.
- Ningún microservicio tiene entidades JPA que apunten a tablas ajenas. Si un servicio necesita datos de otro, debe consultarlo mediante gRPC.

## Consecuencias
- **Positivas:** Reducción drástica de costos de infraestructura en Azure (un solo servidor Flexible Server o contenedor), simplicidad en backups y consistencia operativa.
- **Negativas:** Posibilidad teórica de acoplamiento accidental en base de datos si no se respeta la convención de repositorios independientes.

