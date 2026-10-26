# Despliegue en Render

Un repositorio de backend. Cuatro Web Services. Una sola base `Hospital_triage`.

No hace falta una rama por módulo. El push de siempre al repo del backend lleva los cuatro. Render no los levanta juntos: cada Web Service elige su jar con la variable `MODULE`.

La base de datos no se despliega como aplicación. El repo de la base sigue siendo el esquema. La conexión es la de Supabase: la misma `DATABASE_URL` en pacientes, evaluación e IA. La cola no tiene base. Gemini solo va en IA, con `GEMINI_API_KEY`.

## Servicios

| Web Service | Qué arranca | Salud |
|---|---|---|
| `ms-pacientes-auth` | pacientes y login | `GET /api/v1/health` |
| `ms-evaluacion-triaje` | signos y score | `GET /api/v1/triage/health` |
| `ms-cola-espera` | orden de la sala | `GET /api/v1/triage/health` |
| `ms-ia-recomendacion` | Gemini y catálogo | `GET /api/v1/ia/health` |

Los contratos (`triage-common-contracts`, `triage-grpc-contracts`) y `legacy-monolith` no se despliegan.

## Variables

Están listas para copiar en `deploy/`:

- `deploy/ms-pacientes-auth.env`
- `deploy/ms-evaluacion-triaje.env`
- `deploy/ms-cola-espera.env`
- `deploy/ms-ia-recomendacion.env`

`DATABASE_URL` es la cadena de Supabase. La misma en pacientes, evaluación e IA. `GEMINI_API_KEY` solo en IA. `CORS_ORIGINS` es la URL del frontend, la misma en los cuatro.

gRPC no usa la URL pública `onrender.com`. Usa el nombre del otro servicio dentro de Render, como en esos archivos. Los cuatro Web Services tienen que estar en la misma región.

En el frontend, las variables están en `Triage_frotend-/deploy/frontend.env`. Son las cuatro URLs públicas `https://...onrender.com`.

`render.yaml` declara los cuatro servicios. No crea otra base: en Render pegas la `DATABASE_URL` de Supabase.
