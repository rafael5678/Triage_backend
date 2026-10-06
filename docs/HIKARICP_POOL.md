# Configuración del Pool HikariCP

Parámetros calibrados para entornos de alta disponibilidad:

- `maximum-pool-size`: 10 conexiones simultáneas (ajustado al plan gratuito de Supabase/Render).
- `minimum-idle`: 2 conexiones listas para respuesta inmediata.
- `idle-timeout`: 30000 ms.
- `connection-timeout`: 20000 ms para manejar reconexiones ante cold-starts.
