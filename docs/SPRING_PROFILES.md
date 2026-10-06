# Perfiles de Ejecución (`spring.profiles.active`)

- `dev`: Base de datos PostgreSQL local en Docker Compose, logging en nivel `DEBUG`.
- `test`: Base de datos en memoria H2 para ejecución ultrarrápida de pruebas unitarias.
- `prod`: Conexión cifrada a Supabase / Render con logging en formato JSON para agregadores de logs.
