# ADR-007: Gestión Centralizada de Secretos mediante Azure Key Vault

## Estado
Aceptado

## Contexto
El sistema maneja información confidencial: cadenas de conexión a PostgreSQL, claves de API para Google Gemini, tokens de Meta WhatsApp y contraseñas de arranque de administradores. Guardar estos valores en texto claro en repositorios Git, archivos YAML o imágenes Docker representa una vulnerabilidad crítica de seguridad (OWASP A02:2021).

## Decisión
Se adopta **Azure Key Vault** para el almacenamiento y ciclo de vida de credenciales en producción:
- Secretos almacenados:
  - `database-url`: Cadena JDBC de PostgreSQL.
  - `gemini-api-key`: Clave de IA.
  - `admin-bootstrap-password`: Contraseña del usuario administrador.
- Los secretos se vinculan a Azure Container Apps mediante referencias a secretos administradas (`secretref:`), manteniéndolos encriptados en reposo y en tránsito.

## Consecuencias
- **Positivas:** Cumplimiento de estándares de seguridad hospitalaria, rotación de claves sin modificar código y protección de los secretos ante fugas en el control de versiones.
- **Negativas:** Requiere configuración de permisos RBAC en Azure antes del despliegue.

