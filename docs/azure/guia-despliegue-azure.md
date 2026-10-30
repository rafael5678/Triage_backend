# Guía Paso a Paso de Despliegue en Microsoft Azure

Esta guía detalla el procedimiento exacto para desplegar el backend completo en Azure una vez que se autorice la creación de recursos.

---

## 1. Prerrequisitos
- Suscripción activa en Microsoft Azure (Azure for Students o Pay-As-You-Go).
- Azure CLI instalado (`az login`) o acceso a **Azure Cloud Shell (Bash)** en [portal.azure.com](https://portal.azure.com).
- Repositorio en GitHub con acceso a GitHub Actions.

---

## 2. Paso 1: Creación del Grupo de Recursos y Registro de Contenedores
```bash
# Variables del entorno
RESOURCE_GROUP="triage-rg"
LOCATION="eastus"
ACR_NAME="triageacr$RANDOM"

# 1. Crear Grupo de Recursos
az group create --name $RESOURCE_GROUP --location $LOCATION

# 2. Crear Azure Container Registry
az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Basic --admin-enabled true

# Obtener credenciales de ACR
ACR_LOGIN_SERVER=$(az acr show --name $ACR_NAME --query loginServer --output tsv)
ACR_USERNAME=$(az acr credential show --name $ACR_NAME --query username --output tsv)
ACR_PASSWORD=$(az acr credential show --name $ACR_NAME --query "passwords[0].value" --output tsv)
```

---

## 3. Paso 2: Creación de Azure Key Vault y Secretos
```bash
KEYVAULT_NAME="triage-kv-$RANDOM"

az keyvault create --resource-group $RESOURCE_GROUP --name $KEYVAULT_NAME --location $LOCATION

# Guardar secretos
az keyvault secret set --vault-name $KEYVAULT_NAME --name "db-password" --value "TuClaveSegura123!"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "gemini-api-key" --value "TuClaveGemini"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "admin-password" --value "UrgenciasAdmin26"
```

---

## 4. Paso 3: Creación de Azure Database for PostgreSQL Flexible Server
```bash
az postgres flexible-server create \
  --resource-group $RESOURCE_GROUP \
  --name "triage-pg-$RANDOM" \
  --location $LOCATION \
  --admin-user "triage" \
  --admin-password "TuClaveSegura123!" \
  --sku-name Standard_B1ms \
  --tier Burstable \
  --storage-size 32 \
  --database-name hospital_triaje \
  --yes
```

---

## 5. Paso 4: Creación del Entorno de Azure Container Apps (ACA)
```bash
ENV_NAME="triage-aca-env"

az extension add --name containerapp --upgrade --yes

az containerapp env create \
  --name $ENV_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION
```

---

## 6. Paso 5: Despliegue de los Microservicios
1. **Microservicios Internos:** Se despliegan con `--ingress internal`.
2. **API Gateway:** Se despliega con `--ingress external --target-port 8080`.
3. El FQDN público resultante del API Gateway se configura en el frontend de Vercel como `VITE_API_URL`.

---

## 7. Procedimiento de Actualización y Rollback

### Actualización de Versión:
Cuando GitHub Actions construye una nueva imagen con tag de versión o SHA de commit:
```bash
az containerapp update --name api-gateway --resource-group $RESOURCE_GROUP --image "$ACR_LOGIN_SERVER/api-gateway:v2"
```

### Rollback Inmediato:
Azure Container Apps mantiene el historial de revisiones:
```bash
# Listar revisiones previas
az containerapp revision list --name api-gateway --resource-group $RESOURCE_GROUP -o table

# Conmutar tráfico al 100% hacia la revisión anterior que funcionaba
az containerapp ingress traffic set \
  --name api-gateway \
  --resource-group $RESOURCE_GROUP \
  --revision-weight <revision-anterior>=100
```

