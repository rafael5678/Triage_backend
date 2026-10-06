# TriageIA — Backend (hospital-triaje)

Spring Boot 3 + PostgreSQL. Persistencia real de pacientes, signos y cola.

## Docker (contenedor nuevo, no usa tu `mi-postgres`)
```bash
docker compose up -d --build
```
- Postgres: contenedor **`hospital-triaje`** · puerto host **5434** · db `hospital_triaje`
- API: **`hospital-triaje-api`** · http://localhost:8080/api/v1/triage/salud

DBeaver: host `localhost`, puerto `5434`, database `hospital_triaje`, user/password `triage`.

## Render (Web Service nuevo)
Variables:
- `DATABASE_URL` → Internal Database URL de **Hospital_triage**
- `CORS_ORIGINS` → URL de Vercel (ej. `https://tu-app.vercel.app`)
- `PORT` lo asigna Render solo

Dockerfile incluido. Health check: `/api/v1/triage/salud`

Frontend: https://github.com/rafael5678/Triage_frotend-  
SQL: https://github.com/rafael5678/Triage_base-de-datos
