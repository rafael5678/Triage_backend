#!/usr/bin/env bash
# Despliega los cuatro microservicios en Cloud Run desde Cloud Shell.
# Uso: bash deploy/cloudrun.sh   (desde la carpeta del repo, rama develop)
set -euo pipefail

REGION="${REGION:-us-west1}"
REPO="triage"
FRONTEND_DEFAULT="https://clasificador-predictivo-e.vercel.app"

PROJECT="$(gcloud config get-value project 2>/dev/null)"
if [ -z "$PROJECT" ]; then
  echo "No hay proyecto activo. Ejecuta: gcloud config set project TU_PROYECTO"
  exit 1
fi
IMAGE="${REGION}-docker.pkg.dev/${PROJECT}/${REPO}/backend:latest"

read -r -p "URL del frontend en Vercel [${FRONTEND_DEFAULT}]: " CORS_ORIGINS
CORS_ORIGINS="${CORS_ORIGINS:-$FRONTEND_DEFAULT}"
read -r -s -p "DATABASE_URL de Supabase (Session pooler, con la clave puesta): " DATABASE_URL; echo
read -r -s -p "GEMINI_API_KEY: " GEMINI_API_KEY; echo
read -r -s -p "Clave del usuario admin de la app: " ADMIN_PASSWORD; echo

quote() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"; }

env_file() {
  local file
  file="$(mktemp)"
  while [ "$#" -gt 0 ]; do
    printf '%s: %s\n' "$1" "$(quote "$2")" >> "$file"
    shift 2
  done
  echo "$file"
}

deploy() {
  local name="$1" http2="$2" vars="$3"
  local flags=(--image "$IMAGE" --region "$REGION" --allow-unauthenticated
               --memory 512Mi --cpu 1 --min-instances 0 --max-instances 1
               --cpu-boost --env-vars-file "$vars")
  if [ "$http2" = "si" ]; then
    flags+=(--use-http2)
  fi
  gcloud run deploy "$name" "${flags[@]}" --quiet
  rm -f "$vars"
}

url_of() {
  gcloud run services describe "$1" --region "$REGION" --format='value(status.url)'
}

grpc_target() {
  echo "static://${1#https://}:443"
}

echo "== Activando servicios de Google Cloud"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

if ! gcloud artifacts repositories describe "$REPO" --location "$REGION" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$REPO" --repository-format=docker --location "$REGION"
fi

echo "== Construyendo la imagen (compila los cuatro módulos)"
gcloud builds submit --tag "$IMAGE" .

echo "== 1/4 ms-pacientes-auth"
deploy ms-pacientes-auth si "$(env_file \
  MODULE ms-pacientes-auth \
  DATABASE_URL "$DATABASE_URL" \
  CORS_ORIGINS "$CORS_ORIGINS" \
  BOOTSTRAP_ADMIN_USERNAME admin \
  BOOTSTRAP_ADMIN_PASSWORD "$ADMIN_PASSWORD")"
PACIENTES_URL="$(url_of ms-pacientes-auth)"

echo "== 2/4 ms-ia-recomendacion"
deploy ms-ia-recomendacion si "$(env_file \
  MODULE ms-ia-recomendacion \
  DATABASE_URL "$DATABASE_URL" \
  CORS_ORIGINS "$CORS_ORIGINS" \
  GEMINI_API_KEY "$GEMINI_API_KEY" \
  GEMINI_MODEL "gemini-3.5-flash-lite,gemini-3.1-flash-lite,gemini-2.5-flash,gemini-3-flash-preview")"
IA_URL="$(url_of ms-ia-recomendacion)"

echo "== 3/4 ms-evaluacion-triaje"
deploy ms-evaluacion-triaje si "$(env_file \
  MODULE ms-evaluacion-triaje \
  DATABASE_URL "$DATABASE_URL" \
  CORS_ORIGINS "$CORS_ORIGINS" \
  GRPC_NEGOTIATION tls \
  GRPC_CLIENT_PACIENTES "$(grpc_target "$PACIENTES_URL")" \
  GRPC_CLIENT_IA "$(grpc_target "$IA_URL")")"
EVALUACION_URL="$(url_of ms-evaluacion-triaje)"

echo "== 4/4 ms-cola-espera"
deploy ms-cola-espera no "$(env_file \
  MODULE ms-cola-espera \
  CORS_ORIGINS "$CORS_ORIGINS" \
  GRPC_NEGOTIATION tls \
  GRPC_CLIENT_EVALUACION "$(grpc_target "$EVALUACION_URL")")"
COLA_URL="$(url_of ms-cola-espera)"

cat <<EOF

Listo. Pega esto en Vercel (Settings -> Environment Variables) y haz Redeploy:

VITE_AUTH_URL=${PACIENTES_URL}
VITE_EVAL_URL=${EVALUACION_URL}
VITE_QUEUE_URL=${COLA_URL}
VITE_AI_URL=${IA_URL}
EOF
