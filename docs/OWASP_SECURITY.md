# Políticas de Seguridad HTTP (OWASP)

El microservicio incluye protección de cabeceras en cada respuesta:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- Bloqueo de ataques de inyección y sniffing de tipos MIME.
