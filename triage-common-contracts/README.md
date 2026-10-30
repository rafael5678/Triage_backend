# TriageIA — Common Contracts (`triage-common-contracts`)

**Librería compartida de contratos DTOs, Enums y Excepciones para la comunicación REST y transporte de datos de TriageIA.**

---

## 1. Principio Arquitectónico
- **Cero acoplamiento a la base de datos:** Este módulo no incluye dependencias de `spring-boot-starter-data-jpa`, Hibernate ni entidades persistentes.
- Diseñado para ser consumido por todos los microservicios y el API Gateway para intercambio de objetos de transferencia de datos limpios y tipados.

---

## 2. Contenido del Módulo

### DTOs Principales
- `PatientIntakeDTO`: Datos demográficos y de ingreso del paciente.
- `VitalSignsDTO`: Mediciones de signos vitales (presión, saturación, pulso, temperatura, etc.).
- `RankedPatientDTO`: Vista integral del paciente clasificado en triaje, con tiempos y destino.
- `QueueStateDTO`: Estado consolidado de la sala de espera.
- `AiAssistDTO`: Recomendaciones estructuradas emitidas por el modelo de IA.
- `SessionUserDTO`: Datos de sesión del usuario autenticado.
- `LoginRequest`, `CreateUserRequest`: Credenciales de acceso y registro de personal.

### Enumeraciones de Dominio
- `TriageLevel`: `TRIAGE_I_ROJO`, `TRIAGE_II_NARANJA`, `TRIAGE_III_AMARILLO`, `TRIAGE_IV_VERDE`, `TRIAGE_V_AZUL`.
- `CareStatus`: `EN_ESPERA`, `EN_ATENCION`, `ATENDIDO`, `CANCELADO`.
- `CareArea`: Destinos en el hospital (`REANIMACION`, `URGENCIAS_CRITICAS`, `CONSULTA_RAPIDA`, etc.).
- `UserRole`: `ADMIN`, `MEDICO`, `ENFERMERO`, `RECEPCION`.

