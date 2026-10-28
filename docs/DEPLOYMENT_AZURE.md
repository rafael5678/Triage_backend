# Despliegue en Azure Container Apps

Cuatro Container Apps, una sola imagen. Cada app elige su jar con `MODULE`. La base es Supabase; la cola no tiene base.

La imagen la construye GitHub Actions (`.github/workflows/imagen.yml`) en cada push a `develop` y la publica en `ghcr.io/rafael5678/triage-backend:latest`. Azure solo la descarga: así no hace falta Azure Container Registry ni sus builds, que las suscripciones de estudiante suelen tener bloqueados.

## gRPC

Igual que en Cloud Run: un puerto por app. Pacientes, IA y evaluación usan ingress con `--transport http2`, y Jetty atiende REST y gRPC en h2c. Las llamadas internas van a la dirección `https://...azurecontainerapps.io` del otro servicio en el puerto 443 con TLS (`GRPC_NEGOTIATION=tls`).

## Pasos

1. Activar [Azure for Students](https://azure.microsoft.com/free/students) con el correo de la universidad. No pide tarjeta.
2. Que la imagen sea pública una vez: en GitHub, Packages → `triage-backend` → Package settings → Change visibility → Public.
3. En [portal.azure.com](https://portal.azure.com), abrir Cloud Shell en modo Bash:

```bash
git clone -b develop https://github.com/rafael5678/Triage_backend.git
cd Triage_backend
bash deploy/azure.sh
```

El script pide la URL del frontend, la `DATABASE_URL` de Supabase, la clave de Gemini y la del admin. Al final imprime las cuatro `VITE_` para Vercel.

Si la región `westus2` no está permitida en la suscripción, se elige otra: `LOCATION=eastus bash deploy/azure.sh`.

El entorno se crea con workload profiles (plan Consumption), no en modo express: express no acepta ingress HTTP/2 y gRPC lo necesita.

Para desplegar un cambio: push a `develop`. GitHub construye la imagen y, si existe el secreto `AZURE_CREDENTIALS`, actualiza solo los 4 servicios en Azure.

Configuración única del secreto, en Azure Cloud Shell:

```bash
az ad sp create-for-rbac --name triage-github-deploy --role contributor \
  --scopes $(az group show -n triage-brazilsouth --query id -o tsv) --json-auth
```

El JSON que imprime va completo en GitHub: repositorio `Triage_backend` → Settings → Secrets and variables → Actions → New repository secret, con el nombre `AZURE_CREDENTIALS`.

Sin ese secreto, se actualiza a mano:

```bash
for app in ms-pacientes-auth ms-ia-recomendacion ms-evaluacion-triaje ms-cola-espera; do
  az containerapp update -n $app -g triage-brazilsouth --image ghcr.io/rafael5678/triage-backend:latest --revision-suffix r$(date +%s) -o none
done
```

## Variables por app

| App | Transporte | Variables |
|---|---|---|
| `ms-pacientes-auth` | http2 | `MODULE`, `PORT=8080`, `DATABASE_URL`, `CORS_ORIGINS`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` |
| `ms-ia-recomendacion` | http2 | `MODULE`, `PORT=8080`, `DATABASE_URL`, `CORS_ORIGINS`, `GEMINI_API_KEY`, `GEMINI_MODEL` |
| `ms-evaluacion-triaje` | http2 | `MODULE`, `PORT=8080`, `DATABASE_URL`, `CORS_ORIGINS`, `GRPC_NEGOTIATION=tls`, `GRPC_CLIENT_PACIENTES`, `GRPC_CLIENT_IA` |
| `ms-cola-espera` | auto | `MODULE`, `PORT=8080`, `CORS_ORIGINS`, `GRPC_NEGOTIATION=tls`, `GRPC_CLIENT_EVALUACION` |

Las claves (`DATABASE_URL`, `GEMINI_API_KEY`, `BOOTSTRAP_ADMIN_PASSWORD`) quedan como secretos de cada app.

## Costo

Cada app escala a cero y usa 0,5 vCPU y 1 GiB. Container Apps da una cuota gratis mensual por suscripción; lo que pase de ahí sale del crédito de estudiante. El entorno se crea sin Log Analytics para no gastar en logs.

La primera petición después de un rato puede tardar hasta un minuto mientras despiertan los servicios.
