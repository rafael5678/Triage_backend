#!/usr/bin/env bash
# Crea en Azure una tarea programada que cada 15 minutos revisa si GitHub publicó una imagen nueva
# del backend y, si la hay, actualiza los 4 microservicios. Se corre una sola vez desde Cloud Shell.
# Usa una identidad administrada del propio Azure, así que no necesita crear una aplicación en el
# directorio de la universidad (eso está bloqueado para cuentas de estudiante).
set -euo pipefail

RG="${RG:-triage-brazilsouth}"
ENV_NAME="${ENV_NAME:-triage-grpc}"
JOB="${JOB:-triage-autoupdate}"
CRON="${CRON:-*/15 * * * *}"

SCRIPT=$(cat <<'EOF'
set -e
az login --identity -o none
TOKEN=$(curl -fsS "https://ghcr.io/token?scope=repository:rafael5678/triage-backend:pull" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
DIGEST=$(curl -fsSI -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json" \
  https://ghcr.io/v2/rafael5678/triage-backend/manifests/latest | tr -d '\r' | awk -F': ' 'tolower($1)=="docker-content-digest"{print $2}')
if [ -z "$DIGEST" ]; then echo "No se pudo leer la imagen de GitHub"; exit 1; fi
IMAGE="ghcr.io/rafael5678/triage-backend@$DIGEST"
for app in ms-pacientes-auth ms-ia-recomendacion ms-evaluacion-triaje ms-cola-espera; do
  CURRENT=$(az containerapp show -n "$app" -g "$RG" --query "properties.template.containers[0].image" -o tsv)
  if [ "$CURRENT" != "$IMAGE" ]; then
    echo "Actualizando $app a $IMAGE"
    az containerapp update -n "$app" -g "$RG" --image "$IMAGE" -o none
  else
    echo "$app ya está al día"
  fi
done
EOF
)

echo "== Preparando Azure"
az extension add --name containerapp --upgrade --only-show-errors --yes

PROFILE_ARGS=()
if az containerapp env show -n "$ENV_NAME" -g "$RG" -o json | grep -q '"workloadProfileType"'; then
  PROFILE_ARGS=(--workload-profile-name Consumption)
fi

if az containerapp job show -n "$JOB" -g "$RG" >/dev/null 2>&1; then
  echo "== La tarea $JOB ya existe; se actualiza el script"
  az containerapp job update -n "$JOB" -g "$RG" --cron-expression "$CRON" \
    --set-env-vars RG="$RG" --command "/bin/bash" --args "-c" "$SCRIPT" -o none
else
  echo "== Creando la tarea $JOB (cada 15 minutos)"
  az containerapp job create -n "$JOB" -g "$RG" --environment "$ENV_NAME" \
    --trigger-type Schedule --cron-expression "$CRON" \
    --replica-timeout 900 --replica-retry-limit 0 --parallelism 1 --replica-completion-count 1 \
    --image mcr.microsoft.com/azure-cli:latest --cpu 0.5 --memory 1.0Gi \
    "${PROFILE_ARGS[@]}" \
    --mi-system-assigned \
    --env-vars RG="$RG" \
    --command "/bin/bash" --args "-c" "$SCRIPT" -o none
fi

echo "== Dándole permiso para actualizar solo este grupo de recursos"
PRINCIPAL=$(az containerapp job show -n "$JOB" -g "$RG" --query identity.principalId -o tsv)
SCOPE=$(az group show -n "$RG" --query id -o tsv)
if ! az role assignment list --assignee "$PRINCIPAL" --scope "$SCOPE" --role Contributor --query "[0].id" -o tsv 2>/dev/null | grep -q .; then
  az role assignment create --assignee-object-id "$PRINCIPAL" --assignee-principal-type ServicePrincipal \
    --role Contributor --scope "$SCOPE" -o none
fi

echo "== Esperando un minuto a que Azure active el permiso y probando la tarea"
sleep 60
az containerapp job start -n "$JOB" -g "$RG" -o none

cat <<MSG

Listo. La tarea $JOB revisa GitHub cada 15 minutos y actualiza los 4 servicios si hay una imagen nueva.
Para ver sus ejecuciones:
  az containerapp job execution list -n $JOB -g $RG -o table
MSG
