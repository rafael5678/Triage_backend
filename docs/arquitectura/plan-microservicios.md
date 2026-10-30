# Plan de Microservicios y Transición desde el Monolito

Este documento resume la ruta de evolución arquitectónica desde el monolito legado (`legacy-monolith`) hacia la arquitectura de microservicios actual.

---

## 1. Justificación de la Descomposición
El monolito original presentaba acoplamiento entre la persistencia de usuarios, la toma de constantes vitales y las llamadas externas a Google Gemini y WhatsApp. Ante fallas en la API de IA o bloqueos de red, el ciclo de atención del triaje corría riesgo de congelarse.

## 2. Estrategia de Separación de Fases
1. **Fase 1: Extracción de Contratos:**
   - Creación de `triage-common-contracts` para DTOs y tipos de dominio neutrales.
   - Creación de `triage-grpc-contracts` para definir formalmente `triage.proto`.
2. **Fase 2: Segregación por Servicios Autónomos:**
   - `ms-pacientes-auth`: Ficha demográfica y usuarios.
   - `ms-evaluacion-triaje`: Motor de reglas clínicas y cálculo NEWS2.
   - `ms-ia-recomendacion`: Integración con Gemini, catálogo y cuotas.
   - `ms-cola-espera`: Sala de espera reactiva y SSE.
3. **Fase 3: Incorporación de API Gateway:**
   - `api-gateway`: Enrutamiento unificado, CORS y observabilidad.
4. **Fase 4: Despliegue en Microsoft Azure:**
   - Contenedores independientes en Azure Container Apps con Ingress interno y Gateway público.

