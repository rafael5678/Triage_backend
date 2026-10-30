# Política de Seguridad, Roles y Protección de Datos — TriageIA

## 1. Modelo de Roles Hospitalarios
El sistema implementa cuatro roles según el principio de privilegio mínimo (*Least Privilege*):

| Rol | Alcance y Permisos |
|---|---|
| `ADMIN` | Gestión total de personal hospitalario (`/api/v1/admin/users`), edición del catálogo clínico (`/api/v1/admin/symptoms`), visualización de estadísticas globales. |
| `MEDICO` | Evaluación clínica de triaje, prescripción de destino médico, anulación/reevaluación de clasificaciones y llamado de pacientes. |
| `ENFERMERO` | Registro de signos vitales, aplicación de protocolos clínicos de urgencia y control de sala de espera. |
| `RECEPCION` | Admisión inicial de pacientes, registro demográfico y actualización de presencia en sala. |

---

## 2. Protección de Secretos y Credenciales
- **Contraseñas:** Hasheadas con BCrypt y salt criptográfico en `ms-pacientes-auth`. Ninguna contraseña se almacena en texto plano.
- **Secretos Cloud:** Gestionados en **Azure Key Vault** (`DATABASE_URL`, `GEMINI_API_KEY`, tokens).
- **Prohibición Estricta:** Ningún secreto o token se incluye en repositorios Git ni se expone en imágenes Docker.

---

## 3. Seguridad de Red Perimetral
- Los microservicios de backend operan dentro de una **red privada virtual** en Azure Container Apps o red bridge Docker (`triage-net`).
- Ningún microservicio interno posee dirección IP pública ni puerto expuesto a internet.
- Todo el tráfico exterior es filtrado, validado y registrado por el **API Gateway**.

