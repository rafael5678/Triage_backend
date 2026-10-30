# ADR-001: Adopción de Arquitectura de Microservicios

## Estado
Aceptado

## Contexto
El sistema de triaje hospitalario TriageIA comenzó como una aplicación Spring Boot monolítica (`legacy-monolith`). Sin embargo, los requerimientos del dominio exigen alta disponibilidad en el registro y evaluación de pacientes incluso si las integraciones externas (Gemini IA o Meta WhatsApp) sufren latencias o caídas temporales. Asimismo, la carga de trabajo de consulta constante del tablero de espera difiere radicalmente de la carga de cálculo clínico.

## Decisión
Se decidió descomponer el sistema en 4 microservicios orientados a dominio y un API Gateway:
1. `api-gateway`: Enrutamiento y control de acceso.
2. `ms-pacientes-auth`: Ficha del paciente y usuarios.
3. `ms-evaluacion-triaje`: Motor de cálculo clínico de gravedad.
4. `ms-cola-espera`: Ordenamiento dinámico y streaming SSE.
5. `ms-ia-recomendacion`: Ingesta con LLMs y catálogo.

## Consecuencias
- **Positivas:** Desacoplamiento de fallos (aislamiento de fallas de IA), escalamiento independiente por servicio, modularidad del código y despliegue autónomo en Azure.
- **Negativas:** Mayor complejidad en la orquestación de red y observabilidad distribuida.

