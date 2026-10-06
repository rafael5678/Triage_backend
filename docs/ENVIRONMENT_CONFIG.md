# Configuración de Variables de Entorno

Variables requeridas para el despliegue del microservicio:

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `PORT` | Puerto de escucha HTTP | `8080` |
| `SPRING_DATASOURCE_URL` | Cadena JDBC de PostgreSQL | Localhost / Supabase |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de autenticación | - |
| `CORS_ALLOWED_ORIGINS` | Orígenes web habilitados | `*` o Vercel domain |
