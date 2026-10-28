#!/usr/bin/env bash
# Despliega los cuatro microservicios en Azure Container Apps desde Azure Cloud Shell (Bash).
# La imagen la construye GitHub Actions en ghcr.io; aquí solo se crean los servicios.
# Uso: bash deploy/azure.sh   (desde la carpeta del repo)
set -euo pipefail

LOCATION="${LOCATION:-westus2}"
RG="${RG:-triage-rg}"
ENV_NAME="${ENV_NAME:-triage-grpc}"
IMAGE="${IMAGE:-ghcr.io/rafael5678/triage-backend:latest}"
FRONTEND_DEFAULT="https://clasificador-predictivo-e.vercel.app"

echo "== Revisando que la imagen de GitHub sea pública"
TOKEN="$(curl -fsS "https://ghcr.io/token?scope=repository:rafael5678/triage-backend:pull" \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' || true)"
if ! curl -fsS -o /dev/null \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.docker.distribution.manifest.v2+json" \
    "https://ghcr.io/v2/rafael5678/triage-backend/manifests/latest"; then
  echo "La imagen ${IMAGE} no existe o no es pública todavía."
  echo "En GitHub: Packages -> triage-backend -> Package settings -> Change visibility -> Public."
  exit 1
fi

echo "== Preparando Azure en ${LOCATION}"
az extension add --name containerapp --upgrade --only-show-errors --yes
az provider register --namespace Microsoft.App --wait
az group create -n "$RG" -l "$LOCATION" -o none
# Un entorno express no acepta ingress HTTP/2, y gRPC lo necesita.
# Sin --environment-mode explícito, la extensión puede crear uno express.
PROFILE_ARGS=(--workload-profile-name Consumption)
if ! az containerapp env show -n "$ENV_NAME" -g "$RG" >/dev/null 2>&1; then
  if ! az containerapp env create -n "$ENV_NAME" -g "$RG" -l "$LOCATION" \
      --environment-mode WorkloadProfiles --logs-destination none -o none; then
    echo "== WorkloadProfiles no disponible, se usa ConsumptionOnly"
    az containerapp env delete -n "$ENV_NAME" -g "$RG" --yes -o none 2>/dev/null || true
    az containerapp env create -n "$ENV_NAME" -g "$RG" -l "$LOCATION" \
      --environment-mode ConsumptionOnly --logs-destination none -o none
  fi
fi
ENV_INFO="$(az containerapp env show -n "$ENV_NAME" -g "$RG" -o json)"
if echo "$ENV_INFO" | grep -qi '"environmentMode": *"express"'; then
  echo "El entorno ${ENV_NAME} quedó en modo express y no acepta HTTP/2."
  echo "Bórralo con: az containerapp env delete -n ${ENV_NAME} -g ${RG} --yes"
  exit 1
fi
if ! echo "$ENV_INFO" | grep -q '"workloadProfileType"'; then
  PROFILE_ARGS=()
fi
echo "== Entorno ${ENV_NAME} listo"

read -r -p "URL del frontend en Vercel [${FRONTEND_DEFAULT}]: " CORS_ORIGINS
CORS_ORIGINS="${CORS_ORIGINS:-$FRONTEND_DEFAULT}"
read -r -s -p "DATABASE_URL de Supabase (Session pooler, con la clave puesta): " DATABASE_URL; echo
read -r -s -p "GEMINI_API_KEY: " GEMINI_API_KEY; echo
read -r -s -p "Clave del usuario admin de la app: " ADMIN_PASSWORD; echo

SECRETS=()

upsert() {
  local name="$1" transport="$2"
  shift 2
  if az containerapp show -n "$name" -g "$RG" >/dev/null 2>&1; then
    if [ "${#SECRETS[@]}" -gt 0 ]; then
      az containerapp secret set -n "$name" -g "$RG" --secrets "${SECRETS[@]}" -o none
    fi
    az containerapp update -n "$name" -g "$RG" --image "$IMAGE" \
      --revision-suffix "r$(date +%s)" --set-env-vars "$@" -o none
  else
    local extra=()
    if [ "${#SECRETS[@]}" -gt 0 ]; then
      extra=(--secrets "${SECRETS[@]}")
    fi
    az containerapp create -n "$name" -g "$RG" --environment "$ENV_NAME" --image "$IMAGE" \
      "${PROFILE_ARGS[@]}" \
      --target-port 8080 --ingress external --transport "$transport" \
      --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 1 \
      "${extra[@]}" --env-vars "$@" -o none
  fi
}

fqdn() {
  az containerapp show -n "$1" -g "$RG" --query properties.configuration.ingress.fqdn -o tsv
}

echo "== 1/4 ms-pacientes-auth"
SECRETS=("dburl=${DATABASE_URL}" "admin=${ADMIN_PASSWORD}")
upsert ms-pacientes-auth http2 \
  MODULE=ms-pacientes-auth PORT=8080 \
  DATABASE_URL=secretref:dburl CORS_ORIGINS="$CORS_ORIGINS" \
  BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD=secretref:admin
PACIENTES="$(fqdn ms-pacientes-auth)"

echo "== 2/4 ms-ia-recomendacion"
SECRETS=("dburl=${DATABASE_URL}" "gemini=${GEMINI_API_KEY}")
upsert ms-ia-recomendacion http2 \
  MODULE=ms-ia-recomendacion PORT=8080 \
  DATABASE_URL=secretref:dburl CORS_ORIGINS="$CORS_ORIGINS" \
  GEMINI_API_KEY=secretref:gemini GEMINI_MODEL=gemini-3.8-flash,gemini-3.5-flash,gemini-3.5-flash-lite
IA="$(fqdn ms-ia-recomendacion)"

echo "== 3/4 ms-evaluacion-triaje"
SECRETS=("dburl=${DATABASE_URL}")
upsert ms-evaluacion-triaje http2 \
  MODULE=ms-evaluacion-triaje PORT=8080 \
  DATABASE_URL=secretref:dburl CORS_ORIGINS="$CORS_ORIGINS" \
  GRPC_NEGOTIATION=tls \
  GRPC_CLIENT_PACIENTES="static://${PACIENTES}:443" \
  GRPC_CLIENT_IA="static://${IA}:443"
EVALUACION="$(fqdn ms-evaluacion-triaje)"

echo "== 4/4 ms-cola-espera"
SECRETS=()
upsert ms-cola-espera auto \
  MODULE=ms-cola-espera PORT=8080 \
  CORS_ORIGINS="$CORS_ORIGINS" \
  GRPC_NEGOTIATION=tls \
  GRPC_CLIENT_EVALUACION="static://${EVALUACION}:443"
COLA="$(fqdn ms-cola-espera)"

cat <<EOF

Listo. Pega esto en Vercel (Settings -> Environment Variables) y haz Redeploy:

VITE_AUTH_URL=https://${PACIENTES}
VITE_EVAL_URL=https://${EVALUACION}
VITE_QUEUE_URL=https://${COLA}
VITE_AI_URL=https://${IA}
EOF
