# Despliegue en Microsoft Azure Container Apps (TriageIA)

Cinco Container Apps, una sola imagen unificada en GitHub Container Registry (`ghcr.io/rafael5678/triage-backend:latest`). Cada app selecciona su JAR en tiempo de ejecución con la variable de entorno `MODULE`.

- **Base de datos**: Supabase PostgreSQL (Session pooler con SSL).
- **Frontend**: Vercel (React + Vite).
- **Grupo de recursos**: `triage-brazilsouth` (Región: `brazilsouth`).
- **Entorno ACA**: `triage-grpc` (WorkloadProfiles / Consumption).

---

## 1. Aplicaciones del Backend

| Aplicación | Ingress | Transporte | Puerto | Módulo JAR | Variables Principales |
|---|---|---|---|---|---|
| `api-gateway` | Externo | auto | 8080 | `api-gateway` | `PORT=8080`, `CORS_ORIGINS`, `MS_PACIENTES_URL`, `MS_EVALUACION_URL`, `MS_COLA_URL`, `MS_IA_URL` |
| `ms-pacientes-auth` | Externo/Interno | http2 | 8080 | `ms-pacientes-auth` | `PORT=8080`, `DATABASE_URL`, `CORS_ORIGINS`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` |
| `ms-ia-recomendacion` | Externo/Interno | http2 | 8080 | `ms-ia-recomendacion` | `PORT=8080`, `DATABASE_URL`, `CORS_ORIGINS`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_DAILY_LIMIT` |
| `ms-evaluacion-triaje` | Externo/Interno | http2 | 8080 | `ms-evaluacion-triaje` | `PORT=8080`, `DATABASE_URL`, `CORS_ORIGINS`, `GRPC_NEGOTIATION=tls`, `GRPC_CLIENT_PACIENTES`, `GRPC_CLIENT_IA` |
| `ms-cola-espera` | Externo/Interno | auto | 8080 | `ms-cola-espera` | `PORT=8080`, `CORS_ORIGINS`, `GRPC_NEGOTIATION=tls`, `GRPC_CLIENT_EVALUACION` |

---

## 2. Comandos para Desplegar desde Azure Cloud Shell (Bash)

Abre [portal.azure.com](https://portal.azure.com) y activa Azure Cloud Shell en modo **Bash**:

```bash
# 1. Clonar o actualizar el repositorio en Cloud Shell
cd ~/Triage_backend 2>/dev/null || git clone https://github.com/rafael5678/Triage_backend.git ~/Triage_backend
cd ~/Triage_backend
git fetch origin
git checkout main
git pull origin main

# 2. Despliegue completo inicial o reconfiguración
LOCATION=brazilsouth RG=triage-brazilsouth bash deploy/azure.sh
```

El script solicitará interactivamente:
1. URL del frontend en Vercel (`https://clasificador-predictivo-e.vercel.app`).
2. `DATABASE_URL` de Supabase (Session Pooler, puerto 5432 o 6543 con clave y SSL).
3. `GEMINI_API_KEY` (Clave de Google AI Studio).
4. Clave del administrador inicial.

---

## 3. Comandos para Actualizar Contenedores Rápidamente

Cuando se hace un `git push` a `main` o `develop`, GitHub Actions compila automáticamente la imagen Docker y la publica en GHCR. Para actualizar los 5 servicios en Azure:

```bash
# Actualizar todos los servicios a la última versión construida
for app in ms-pacientes-auth ms-ia-recomendacion ms-evaluacion-triaje ms-cola-espera api-gateway; do
  echo "== Actualizando $app =="
  az containerapp update -n "$app" -g triage-brazilsouth \
    --image ghcr.io/rafael5678/triage-backend:latest \
    --revision-suffix "r$(date +%s)" -o none
done
```

---

## 4. Diagnóstico y Logs en Azure

```bash
# Ver estado general de las Container Apps
az containerapp list -g triage-brazilsouth -o table

# Ver logs de consola en tiempo real
az containerapp logs show -n ms-pacientes-auth -g triage-brazilsouth --type console --tail 40 --format text
az containerapp logs show -n api-gateway -g triage-brazilsouth --type console --tail 40 --format text
```

---

## 5. Configuración del Frontend en Vercel

En Vercel (Project Settings -> Environment Variables), define las siguientes variables:

```env
VITE_AUTH_URL=https://<FQDN_API_GATEWAY>
VITE_EVAL_URL=https://<FQDN_API_GATEWAY>
VITE_QUEUE_URL=https://<FQDN_API_GATEWAY>
VITE_AI_URL=https://<FQDN_API_GATEWAY>
```

Y ejecuta **Redeploy**. Con esta configuración, todo el tráfico pasa de forma segura por el API Gateway sin requerir ningún cambio de código en la aplicación React.
