# Arquitectura de Despliegue en Microsoft Azure — TriageIA

## 1. Topología Cloud en Azure

```mermaid
flowchart TD
    subgraph Internet["Zona Pública (Internet)"]
        CLIENTE["Navegador Web / Vercel"]
    end

    subgraph AzureSub["Suscripción de Microsoft Azure"]
        subgraph ACR_GRP["Azure Container Registry (ACR)"]
            ACR["triageacr.azurecr.io\n• api-gateway\n• ms-pacientes-auth\n• ms-evaluacion-triaje\n• ms-cola-espera\n• ms-ia-recomendacion"]
        end

        subgraph ACA_ENV["Azure Container Apps Environment (triage-aca-env)"]
            GW_APP["api-gateway\n[Ingress: EXTERNAL (Port 8080)]\nFQDN: https://triage-gateway.westus2.azurecontainerapps.io"]
            
            subgraph PrivateApps["Red Interna Virtual (Ingress: INTERNAL)"]
                MS1["ms-pacientes-auth\n(Port 8081 / gRPC & REST)"]
                MS2["ms-evaluacion-triaje\n(Port 8082 / gRPC & REST)"]
                MS3["ms-cola-espera\n(Port 8083 / REST & SSE)"]
                MS4["ms-ia-recomendacion\n(Port 8084 / gRPC & REST)"]
            end
        end

        subgraph Security["Seguridad"]
            AKV["Azure Key Vault (triage-kv)\n• db-password\n• gemini-api-key\n• admin-password"]
        end

        subgraph Data["Persistencia Administrada"]
            PG["Azure Database for PostgreSQL Flexible Server\n(hospital_triaje / Port 5432)"]
        end

        subgraph Monitoring["Observabilidad"]
            AI["Application Insights & Log Analytics Workspace"]
        end
    end

    CLIENTE -->|HTTPS :443| GW_APP
    GW_APP -->|HTTP Interno| MS1
    GW_APP -->|HTTP Interno| MS2
    GW_APP -->|HTTP Interno| MS3
    GW_APP -->|HTTP Interno| MS4

    MS2 -.->|gRPC Interno :8081| MS1
    MS2 -.->|gRPC Interno :8084| MS4
    MS3 -.->|gRPC Interno :8082| MS2

    MS1 -->|SSL| PG
    MS2 -->|SSL| PG
    MS4 -->|SSL| PG

    AKV -.->|Secret Reference| GW_APP
    AKV -.->|Secret Reference| MS1
    AKV -.->|Secret Reference| MS2
    AKV -.->|Secret Reference| MS4

    ACA_ENV -->|Telemetría| AI
```

---

## 2. Componentes y Funciones
1. **Azure Container Registry (ACR):** Almacén seguro y privado de imágenes Docker multi-stage construidas por GitHub Actions.
2. **Azure Container Apps (ACA):** Aloja las cinco aplicaciones como contenedores serverless con autoscaling (0 a 1 réplica en modo de bajo costo).
3. **Azure Database for PostgreSQL Flexible Server:** Base de datos PostgreSQL relacional administrada con copias de seguridad automáticas y alta disponibilidad.
4. **Azure Key Vault:** Gestión segura de secretos y cadenas de conexión sin exponerlas en repositorios.
5. **Azure Monitor / Application Insights:** Trazabilidad de logs y diagnósticos de salud distribuidos.

