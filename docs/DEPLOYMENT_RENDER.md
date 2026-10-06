# Despliegue en Render Cloud

El servicio se despliega en Render como un **Web Service Dockerizado**.

## Archivos Clave
- `Dockerfile`: Construcción multi-etapa con Maven y Eclipse Temurin JRE 17/21.
- `render.yaml`: Manifiesto declarativo de infraestructura como código (IaC).

## Verificación de Salud (*Health Check*)
- Ruta: `GET /api/triage/cola` (responde 200 cuando el servicio está listo).
