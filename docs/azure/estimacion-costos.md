# Estimación de Costos y Presupuesto Cloud — Microsoft Azure

Este análisis permite conocer detalladamente el consumo mensual estimado antes de autorizar la creación de recursos en Microsoft Azure.

---

## 1. Tabla de Estimación de Costos (Modo Estudiante / Bajo Consumo)

| Servicio Azure | Nivel / SKU | Configuración | Costo Estimado (USD/mes) |
|---|---|---|---|
| **Azure Container Apps (ACA)** | Consumption Profile | 5 microservicios con escalado a cero (0 a 1 réplica, 0.5 vCPU, 1 GiB RAM). | **$0.00 – $5.00** *(Azure incluye cuota gratuita mensual de 180,000 vCPU-segundos y 360,000 GiB-segundos).* |
| **Azure Container Registry (ACR)** | Basic | Almacenamiento de imágenes de contenedor (hasta 10 GB). | **~$5.00** / mes |
| **Azure Key Vault** | Standard | Almacenamiento de 5 secretos y operaciones criptográficas. | **<$0.50** / mes |
| **Log Analytics & Monitor** | Per GB | Logs de diagnóstico de contenedores. | **<$1.00** / mes |
| **Azure Database for PostgreSQL Flexible Server** | Burstable B1ms | 1 vCPU, 2 GiB RAM, 32 GB almacenamiento SSD. | **~$15.00 – $25.00** / mes |
| **TOTAL ESTIMADO:** | | | **~$20.00 – $35.00 USD / mes** |

---

## 2. Estrategias de Ahorro y Nivel Gratuito
1. **Azure for Students:**  
   Las cuentas con crédito educativo de \$100 USD anuales cubren sobradamente varios meses de operación con esta arquitectura.
2. **Apagado Programado de PostgreSQL:**  
   Detener el servidor flexible de PostgreSQL cuando no se estén realizando pruebas ahorra más del 60% del coste de cómputo.
3. **Escalado a Cero de Contenedores:**  
   Cuando no hay solicitudes, los contenedores entran en reposo y no consumen vCPU ni memoria RAM.
4. **Alternativa Gratuita de Base de Datos para Pruebas Académicas:**  
   Si se requiere costo \$0 en base de datos para una demostración puntual, los microservicios en ACA pueden conectarse a una instancia PostgreSQL gratuita (ej. Supabase o Neon) mientras el resto de la arquitectura opera 100% en Azure.

