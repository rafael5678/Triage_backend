# Estructura de DTOs y Mapeo

El desacoplamiento entre el modelo relacional de base de datos y la exposición REST se logra mediante:

- `IngresoPacienteDTO`: Datos brutos ingresados por admisión.
- `RegistroSignosVitalesDTO`: Carga útil fisiológica.
- `PacientePriorizadoDTO`: Respuesta enriquecida con nivel Manchester, color y score calculado.
- `EstadoColaTriageDTO`: Resumen consolidado para monitores de sala.
