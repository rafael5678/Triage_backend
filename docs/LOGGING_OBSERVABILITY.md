# Trazabilidad y Observabilidad

El microservicio utiliza **SLF4J** con implementación **Logback**:

- `INFO`: Registro de admisiones, evaluaciones exitosas y cambios de nivel.
- `WARN`: Casos de descompensación clínica detectados en reevaluaciones.
- `ERROR`: Fallos de conexión a persistencia o datos ilegibles.
