#!/usr/bin/env bash
# ==============================================================================
# TriageIA — Despliegue y Actualización en Azure Container Apps (Cloud Shell)
# Conserva la infraestructura existente en triage-brazilsouth sin destruir recursos.
# Despliega api-gateway como punto de entrada público y preserva los microservicios.
# ==============================================================================
set -euo pipefail

RG="${RG:-triage-brazilsouth}"
LOCATION="${LOCATION:-brazilsouth}"
ENV_NAME="${ENV_NAME:-triage-grpc}"
IMAGE="${IMAGE:-ghcr.io/rafael5678/triage-backend:latest}"
FRONTEND_DEFAULT="https://clasificador-predictivo-e.vercel.app"

echo "=========================================================="
echo " TriageIA — Auditoría y Despliegue en Microsoft Azure"
echo " Grupo de Recursos : $RG"
echo " Región           : $LOCATION"
echo " Entorno ACA      : $ENV_NAME"
echo " Imagen GHCR      : $IMAGE"
echo "=========================================================="

# 1. Verificar sesión activa de Azure
echo "[1/7] Comprobando sesión activa en Azure..."
ACCOUNT_NAME=$(az account show --query name -o tsv 2>/dev/null || true)
if [ -z "$ACCOUNT_NAME" ]; then
    echo "ERROR: No hay una sesión activa de Azure. Ejecuta 'az login' antes de continuar."
    exit 1
fi
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
echo "Sesión activa: $ACCOUNT_NAME (Suscripción: $SUBSCRIPTION_ID)"

# 2. Registrar proveedores y extensiones
echo "[2/7] Asegurando extensión containerapp y proveedor Microsoft.App..."
az extension add --name containerapp --upgrade --only-show-errors --yes
az provider register --namespace Microsoft.App --wait

# 3. Comprobar Grupo de Recursos y Entorno
echo "[3/7] Verificando Grupo de Recursos y Entorno de Container Apps..."
if ! az group show -n "$RG" >/dev/null 2>&1; then
    echo "Creando grupo de recursos $RG en $LOCATION..."
    az group create -n "$RG" -l "$LOCATION" -o none
else
    echo "Grupo de recursos $RG verificado (existente)."
fi

PROFILE_ARGS=()
if ! az containerapp env show -n "$ENV_NAME" -g "$RG" >/dev/null 2>&1; then
    echo "Creando entorno $ENV_NAME con WorkloadProfiles..."
    if ! az containerapp env create -n "$ENV_NAME" -g "$RG" -l "$LOCATION" \
        --environment-mode WorkloadProfiles --logs-destination none -o none; then
        echo "WorkloadProfiles no disponible en la región, creando con ConsumptionOnly..."
        az containerapp env create -n "$ENV_NAME" -g "$RG" -l "$LOCATION" \
            --environment-mode ConsumptionOnly --logs-destination none -o none
    fi
else
    echo "Entorno $ENV_NAME verificado (existente)."
fi

ENV_INFO="$(az containerapp env show -n "$ENV_NAME" -g "$RG" -o json)"
if echo "$ENV_INFO" | grep -q '"workloadProfileType"'; then
    PROFILE_ARGS=(--workload-profile-name Consumption)
fi

# 4. Parámetros y Secretos
echo "[4/7] Configurando parámetros del entorno..."
if [ -z "${CORS_ORIGINS:-}" ]; then
    read -r -p "URL del frontend en Vercel [$FRONTEND_DEFAULT]: " CORS_INPUT
    CORS_ORIGINS="${CORS_INPUT:-$FRONTEND_DEFAULT}"
fi

# Detectar secretos si ya existen en las apps para no volver a pedirlos innecesariamente
EXISTING_SECRETS_AUTH=$(az containerapp secret list -n ms-pacientes-auth -g "$RG" --query "[].name" -o tsv 2>/dev/null || true)

if ! echo "$EXISTING_SECRETS_AUTH" | grep -q "dburl"; then
    read -r -s -p "DATABASE_URL de Supabase (Session Pooler con password): " DATABASE_URL; echo
else
    echo "Secreto 'dburl' detectado en ms-pacientes-auth existente."
    DATABASE_URL="${DATABASE_URL:-}"
fi

EXISTING_SECRETS_IA=$(az containerapp secret list -n ms-ia-recomendacion -g "$RG" --query "[].name" -o tsv 2>/dev/null || true)
if ! echo "$EXISTING_SECRETS_IA" | grep -q "gemini"; then
    read -r -s -p "GEMINI_API_KEY: " GEMINI_API_KEY; echo
else
    echo "Secreto 'gemini' detectado en ms-ia-recomendacion existente."
    GEMINI_API_KEY="${GEMINI_API_KEY:-}"
fi

if ! echo "$EXISTING_SECRETS_AUTH" | grep -q "admin"; then
    read -r -s -p "Clave para usuario admin de la app: " ADMIN_PASSWORD; echo
else
    echo "Secreto 'admin' detectado en ms-pacientes-auth existente."
    ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
fi

# Función utilitaria para crear o actualizar Container Apps preservando estado
upsert_app() {
    local name="$1"
    local transport="$2"
    local ingress_type="$3"
    local target_port="$4"
    shift 4

    echo "== Desplegando / Actualizando: $name =="
    if az containerapp show -n "$name" -g "$RG" >/dev/null 2>&1; then
        echo "La aplicación $name ya existe. Aplicando actualización de revisión..."
        if [ "$#" -gt 0 ]; then
            az containerapp update -n "$name" -g "$RG" \
                --image "$IMAGE" \
                --revision-suffix "r$(date +%s)" \
                --set-env-vars "$@" -o none
        else
            az containerapp update -n "$name" -g "$RG" \
                --image "$IMAGE" \
                --revision-suffix "r$(date +%s)" -o none
        fi
    else
        echo "Creando nueva aplicación $name..."
        az containerapp create -n "$name" -g "$RG" --environment "$ENV_NAME" \
            --image "$IMAGE" \
            "${PROFILE_ARGS[@]}" \
            --target-port "$target_port" \
            --ingress "$ingress_type" \
            --transport "$transport" \
            --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 1 \
            --env-vars "$@" -o none
    fi
}

get_fqdn() {
    az containerapp show -n "$1" -g "$RG" --query properties.configuration.ingress.fqdn -o tsv 2>/dev/null || true
}

# 5. Despliegue de los 4 Microservicios de Dominio
echo "[5/7] Configurando y actualizando los 4 microservicios..."

# 5.1 ms-pacientes-auth
if [ -n "$DATABASE_URL" ] && [ -n "$ADMIN_PASSWORD" ]; then
    az containerapp secret set -n ms-pacientes-auth -g "$RG" --secrets "dburl=${DATABASE_URL}" "admin=${ADMIN_PASSWORD}" -o none 2>/dev/null || true
fi
upsert_app ms-pacientes-auth http2 external 8080 \
    MODULE=ms-pacientes-auth PORT=8080 \
    DATABASE_URL=secretref:dburl CORS_ORIGINS="$CORS_ORIGINS" \
    BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD=secretref:admin

PACIENTES_FQDN=$(get_fqdn ms-pacientes-auth)

# 5.2 ms-ia-recomendacion
if [ -n "$DATABASE_URL" ] && [ -n "$GEMINI_API_KEY" ]; then
    az containerapp secret set -n ms-ia-recomendacion -g "$RG" --secrets "dburl=${DATABASE_URL}" "gemini=${GEMINI_API_KEY}" -o none 2>/dev/null || true
fi
upsert_app ms-ia-recomendacion http2 external 8080 \
    MODULE=ms-ia-recomendacion PORT=8080 \
    DATABASE_URL=secretref:dburl CORS_ORIGINS="$CORS_ORIGINS" \
    GEMINI_API_KEY=secretref:gemini GEMINI_MODEL="gemini-1.5-flash,gemini-2.0-flash" GEMINI_DAILY_LIMIT=150

IA_FQDN=$(get_fqdn ms-ia-recomendacion)

# 5.3 ms-evaluacion-triaje
if [ -n "$DATABASE_URL" ]; then
    az containerapp secret set -n ms-evaluacion-triaje -g "$RG" --secrets "dburl=${DATABASE_URL}" -o none 2>/dev/null || true
fi
upsert_app ms-evaluacion-triaje http2 external 8080 \
    MODULE=ms-evaluacion-triaje PORT=8080 \
    DATABASE_URL=secretref:dburl CORS_ORIGINS="$CORS_ORIGINS" \
    GRPC_NEGOTIATION=tls \
    GRPC_CLIENT_PACIENTES="static://${PACIENTES_FQDN}:443" \
    GRPC_CLIENT_IA="static://${IA_FQDN}:443"

EVALUACION_FQDN=$(get_fqdn ms-evaluacion-triaje)

# 5.4 ms-cola-espera
upsert_app ms-cola-espera auto external 8080 \
    MODULE=ms-cola-espera PORT=8080 \
    CORS_ORIGINS="$CORS_ORIGINS" \
    GRPC_NEGOTIATION=tls \
    GRPC_CLIENT_EVALUACION="static://${EVALUACION_FQDN}:443"

COLA_FQDN=$(get_fqdn ms-cola-espera)

# 6. Despliegue del API Gateway (Nuevo componente centralizador)
echo "[6/7] Desplegando API Gateway unificado (Ingress Externo)..."
upsert_app api-gateway auto external 8080 \
    MODULE=api-gateway PORT=8080 \
    CORS_ORIGINS="$CORS_ORIGINS" \
    MS_PACIENTES_URL="https://${PACIENTES_FQDN}" \
    MS_EVALUACION_URL="https://${EVALUACION_FQDN}" \
    MS_COLA_URL="https://${COLA_FQDN}" \
    MS_IA_URL="https://${IA_FQDN}"

GATEWAY_FQDN=$(get_fqdn api-gateway)

# 7. Verificación de Salud
echo "[7/7] Comprobando disponibilidad del API Gateway..."
sleep 5
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "https://${GATEWAY_FQDN}/api/gateway/status" || true)
echo "Código HTTP Gateway (/api/gateway/status): $HTTP_CODE"

echo "=========================================================="
echo " DESPLIEGUE FINALIZADO EXITOSAMENTE"
echo "=========================================================="
echo "URL Pública del API Gateway: https://${GATEWAY_FQDN}"
echo ""
echo "Configuración requerida en Vercel (Project Settings -> Environment Variables):"
echo "----------------------------------------------------------"
echo "VITE_AUTH_URL=https://${GATEWAY_FQDN}"
echo "VITE_EVAL_URL=https://${GATEWAY_FQDN}"
echo "VITE_QUEUE_URL=https://${GATEWAY_FQDN}"
echo "VITE_AI_URL=https://${GATEWAY_FQDN}"
echo "----------------------------------------------------------"
echo "Tras actualizar estas variables en Vercel, ejecuta un 'Redeploy'."
echo "=========================================================="

