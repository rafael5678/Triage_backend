# Configuración de Seguridad y CORS

Implementada en `CorsConfig.java` para autorizar peticiones asíncronas desde:
- `http://localhost:5173` (Entorno de desarrollo local Vite).
- Dominios productivos `*.vercel.app`.

Permite los métodos `GET`, `POST`, `PUT`, `DELETE` y `OPTIONS` con cabeceras `Authorization` y `Content-Type`.
