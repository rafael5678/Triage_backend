# Guía de Ejecución Local con Docker Compose — TriageIA

Esta guía permite levantar todo el stack de backend de TriageIA en una máquina local con un único comando.

---

## 1. Requisitos Previos
- Docker Desktop (versión 20+ con Docker Compose v2+).
- Git.
- 4 GB de memoria RAM libre.

---

## 2. Iniciar el Backend Completo
Desde el directorio `Triage_backend/`:

```bash
# Construir y levantar todos los contenedores en segundo plano
docker compose up --build -d
```

### Contenedores Iniciados:
1. `hospital-triaje-db` (PostgreSQL 16 en puerto host `5434`).
2. `ms-pacientes-auth` (Puerto interno `8081`).
3. `ms-ia-recomendacion` (Puerto interno `8084`).
4. `ms-evaluacion-triaje` (Puerto interno `8082`).
5. `ms-cola-espera` (Puerto interno `8083`).
6. `triage-api-gateway` (Puerto host público `8080`).

---

## 3. Verificación de Salud
Comprobar que el API Gateway está respondiendo:
```bash
curl http://localhost:8080/api/gateway/status
```

Respuesta esperada:
```json
{
  "service": "api-gateway",
  "status": "UP",
  "version": "0.1.0"
}
```

---

## 4. Detener el Entorno
```bash
docker compose down
```
Para eliminar también los volúmenes de base de datos:
```bash
docker compose down -v
```

