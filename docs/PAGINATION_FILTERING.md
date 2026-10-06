# Paginación y Filtrado de Cola

Para instituciones hospitalarias con más de 100 pacientes en espera:

- Parámetros `page` y `size` sobre el endpoint `GET /api/triage/cola`.
- Filtro opcional por nivel Manchester (`?nivel=NIVEL_I`).
- Respuestas con metadatos de total de elementos y páginas disponibles.
