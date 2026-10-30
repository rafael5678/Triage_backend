# ADR-004: Incorporación del API Gateway con Spring Cloud Gateway

## Estado
Aceptado

## Contexto
El sistema carecía de un Gateway independiente. El frontend web en React llamaba directamente a 4 microservicios en puertos diferentes (8081, 8082, 8083, 8084). En Azure, esto obligaba a exponer públicamente los cuatro microservicios con 4 dominios FQDN distintos, generando riesgos de seguridad, múltiples configuraciones CORS y problemas de enrutamiento.

## Decisión
Se implementa el módulo `api-gateway` utilizando **Spring Cloud Gateway (Spring Boot 3.3.4)** en el puerto 8080:
- Funciona como único punto de acceso público desde internet.
- Enruta las solicitudes REST y streams SSE hacia los microservicios internos.
- Resuelve colisiones de rutas mediante predicados de método HTTP (ej. `POST /api/v1/triage/patients` hacia `ms-pacientes-auth` vs `GET /api/v1/triage/patients` hacia `ms-cola-espera`).
- Centraliza las políticas de CORS.
- Inyecta `X-Correlation-Id` para trazabilidad distribuida.

## Consecuencias
- **Positivas:** Seguridad perimetral robusta, reducción de la superficie de ataque en Azure a un solo Ingress público, observabilidad centralizada y abstracción de la topología interna.
- **Negativas:** Introduce un salto de red adicional para las peticiones externas.

