#!/usr/bin/env bash
# ==============================================================================
# Script de Despliegue Automatizado de Microservicios TriageIA en Microsoft Azure
# Arquitectura: Azure Container Apps (ACA) con Ingress Interno y API Gateway Público
# ==============================================================================

set -euo pipefail

RG="${AZURE_RESOURCE_GROUP:-triage-rg}"
LOCATION="${AZURE_LOCATION:-eastus}"
ENV_NAME="${ACA_ENVIRONMENT_NAME:-triage-aca-env}"
ACR_NAME="${ACR_NAME:-triageacr$RANDOM}"
CORS_ORIGINS="${CORS_ORIGINS:-http://localhost:5173,https://clasificador-predictivo-e.vercel.app}"

echo "=========================================================="
echo " Despliegue de Arquitectura de Microservicios TriageIA"
echo " Región: $LOCATION | Grupo de Recursos: $RG"
echo "=========================================================="

# 1. Asegurar Grupo de Recursos
echo "[1/6] Creando/Verificando Grupo de Recursos..."
az group create --name "$RG" --location "$LOCATION" -o none

# 2. Azure Container Registry
echo "[2/6] Verificando Azure Container Registry ($ACR_NAME)..."
if ! az acr show --name "$ACR_NAME" --resource-group "$RG" >/dev/null 2>&1; then
    az acr create --resource-group "$RG" --name "$ACR_NAME" --sku Basic --admin-enabled true -o none
fi

ACR_LOGIN_SERVER=$(az acr show --name "$ACR_NAME" --resource-group "$RG" --query loginServer -o tsv)
echo "ACR Server: $ACR_LOGIN_SERVER"

# 3. Entorno de Azure Container Apps
echo "[3/6] Configurando entorno de Azure Container Apps ($ENV_NAME)..."
az extension add --name containerapp --upgrade --only-show-errors --yes
if ! az containerapp env show --name "$ENV_NAME" --resource-group "$RG" >/dev/null 2>&1; then
    az containerapp env create --name "$ENV_NAME" --resource-group "$RG" --location "$LOCATION" -o none
fi

# 4. Compilar y publicar imágenes Docker a ACR
echo "[4/6] Compilando y subiendo imágenes a ACR..."
az acr build --registry "$ACR_NAME" --image "api-gateway:latest" --file api-gateway/Dockerfile .
az acr build --registry "$ACR_NAME" --image "ms-pacientes-auth:latest" --file ms-pacientes-auth/Dockerfile .
az acr build --registry "$ACR_NAME" --image "ms-evaluacion-triaje:latest" --file ms-evaluacion-triaje/Dockerfile .
az acr build --registry "$ACR_NAME" --image "ms-cola-espera:latest" --file ms-cola-espera/Dockerfile .
az acr build --registry "$ACR_NAME" --image "ms-ia-recomendacion:latest" --file ms-ia-recomendacion/Dockerfile .

# 5. Desplegar Microservicios Internos (Ingress Interno)
echo "[5/6] Desplegando Microservicios de Dominio con Ingress Interno..."

# 5.1 Pacientes
az containerapp create --name ms-pacientes-auth --resource-group "$RG" --environment "$ENV_NAME" \
    --image "$ACR_LOGIN_SERVER/ms-pacientes-auth:latest" --target-port 8081 --ingress internal \
    --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 1 \
    --env-vars PORT=8081 CORS_ORIGINS="$CORS_ORIGINS" -o none

# 5.2 IA
az containerapp create --name ms-ia-recomendacion --resource-group "$RG" --environment "$ENV_NAME" \
    --image "$ACR_LOGIN_SERVER/ms-ia-recomendacion:latest" --target-port 8084 --ingress internal \
    --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 1 \
    --env-vars PORT=8084 CORS_ORIGINS="$CORS_ORIGINS" -o none

# 5.3 Evaluación
az containerapp create --name ms-evaluacion-triaje --resource-group "$RG" --environment "$ENV_NAME" \
    --image "$ACR_LOGIN_SERVER/ms-evaluacion-triaje:latest" --target-port 8082 --ingress internal \
    --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 1 \
    --env-vars PORT=8082 CORS_ORIGINS="$CORS_ORIGINS" \
    GRPC_CLIENT_PACIENTES="static://ms-pacientes-auth:8081" \
    GRPC_CLIENT_IA="static://ms-ia-recomendacion:8084" -o none

# 5.4 Cola
az containerapp create --name ms-cola-espera --resource-group "$RG" --environment "$ENV_NAME" \
    --image "$ACR_LOGIN_SERVER/ms-cola-espera:latest" --target-port 8083 --ingress internal \
    --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 1 \
    --env-vars PORT=8083 CORS_ORIGINS="$CORS_ORIGINS" \
    GRPC_CLIENT_EVALUACION="static://ms-evaluacion-triaje:8082" -o none

# 6. Desplegar API Gateway (Ingress Externo)
echo "[6/6] Desplegando API Gateway con Ingress EXTERNO Público..."
az containerapp create --name api-gateway --resource-group "$RG" --environment "$ENV_NAME" \
    --image "$ACR_LOGIN_SERVER/api-gateway:latest" --target-port 8080 --ingress external \
    --cpu 0.5 --memory 1.0Gi --min-replicas 1 --max-replicas 2 \
    --env-vars PORT=8080 \
    MS_PACIENTES_URL="http://ms-pacientes-auth" \
    MS_EVALUACION_URL="http://ms-evaluacion-triaje" \
    MS_COLA_URL="http://ms-cola-espera" \
    MS_IA_URL="http://ms-ia-recomendacion" \
    CORS_ORIGINS="$CORS_ORIGINS" -o none

GATEWAY_FQDN=$(az containerapp show --name api-gateway --resource-group "$RG" --query properties.configuration.ingress.fqdn -o tsv)

echo "=========================================================="
echo " Despliegue Completado Exitosamente en Azure"
echo " FQDN Público del API Gateway: https://$GATEWAY_FQDN"
echo " Configura en el Frontend Vercel: VITE_API_URL=https://$GATEWAY_FQDN"
echo "=========================================================="

