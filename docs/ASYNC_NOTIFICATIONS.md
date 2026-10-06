# Procesamiento Asíncrono de Eventos

Configuración mediante `@EnableAsync`:

- Las tareas de auditoría y recálculo estadístico se despachan a un hilo secundario (`ThreadPoolTaskExecutor`).
- El endpoint REST responde al médico en menos de 50 ms sin bloquearse en cálculos históricos secundarios.
