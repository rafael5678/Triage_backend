# Especificación de Endpoints REST

Base Path: `/api/triage`

### 1. Evaluar Paciente
- **Ruta**: `POST /api/triage/evaluar`
- **Body**: `IngresoPacienteDTO`
- **Respuesta**: `200 OK` con `PacientePriorizadoDTO`

### 2. Obtener Cola Dinámica
- **Ruta**: `GET /api/triage/cola`
- **Respuesta**: `200 OK` con `EstadoColaTriageDTO` (lista ordenada y resumen)

### 3. Reevaluar Paciente
- **Ruta**: `PUT /api/triage/reevaluar`
- **Body**: `ReevaluacionDTO`
- **Respuesta**: `200 OK` con nueva evaluación y posición recalculada
