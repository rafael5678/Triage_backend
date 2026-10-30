# ADR-006: Despliegue en Microsoft Azure con Azure Container Apps (ACA)

## Estado
Aceptado

## Contexto
El backend requiere una plataforma cloud administrada capaz de ejecutar microservicios Java/Spring Boot con escalado automático, soporte para contenedores Docker independientes, tráfico HTTP/2 gRPC y costes controlados para entornos académicos y de producción.

## Decisión
Se selecciona **Azure Container Apps (ACA)** como el entorno de ejecución:
- **Modelo de Trabajo:** Workload Profiles en modo *Consumption* (escala a cero durante inactividad).
- **Ingress:**
  - `api-gateway`: Ingress **Externo** (público en internet con TLS automático).
  - Microservicios de dominio: Ingress **Interno** (solo accesibles dentro del entorno virtual de ACA).
- **Imágenes de Contenedor:** Alojadas en **Azure Container Registry (ACR)** con Dockerfiles multi-stage independientes por cada servicio.

## Consecuencias
- **Positivas:** Reducción drástica de costos (pago por segundo de uso de CPU/RAM), sin gestión de nodos Kubernetes complejos (AKS), y red interna privada automática.
- **Negativas:** Latencia inicial de arranque en frío (*cold start*) cuando una réplica despierta desde cero.

