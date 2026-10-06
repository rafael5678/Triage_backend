# Gestión Transaccional y Concurrencia

Manejo de transacciones en la capa de servicios Spring:

- Uso de `@Transactional(readOnly = true)` en consultas de cola para optimizar el rendimiento de lectura.
- `@Transactional(isolation = Isolation.READ_COMMITTED)` en la creación de evaluaciones para evitar lecturas sucias.
- Bloqueos optimistas para actualizaciones simultáneas de estado de atención.
