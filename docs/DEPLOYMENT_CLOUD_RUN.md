# Despliegue en Google Cloud Run

Cuatro servicios de Cloud Run, una sola imagen. Cada servicio elige su jar con `MODULE`. La base es Supabase; la cola no tiene base.

Render no sirve para esta arquitectura en plan gratis: sus servicios Free no reciben tráfico de la red privada, y su dirección pública no deja pasar gRPC. Cloud Run sí, si el servicio usa HTTP/2 de punta a punta.

## Cómo pasa gRPC

Cloud Run abre un solo puerto por servicio. Pacientes, IA y evaluación sirven REST y gRPC en ese mismo puerto con Jetty en h2c (`server.http2.enabled`). Tomcat no sirve aquí: pierde el código de error gRPC cuando la respuesta no tiene cuerpo.

Esos tres se despliegan con `--use-http2`. La cola solo llama, así que no lo necesita.

Las llamadas internas salen por la URL `https://...run.app` del otro servicio, en el puerto 443 con TLS (`GRPC_NEGOTIATION=tls`).

## Despliegue

En [Cloud Shell](https://shell.cloud.google.com), con un proyecto que tenga cuenta de facturación:

```bash
git clone -b develop https://github.com/rafael5678/Triage_backend.git
cd Triage_backend
gcloud config set project TU_PROYECTO
bash deploy/cloudrun.sh
```

El script pide la URL del frontend, la `DATABASE_URL` de Supabase, la clave de Gemini y la clave del admin. Al final imprime las cuatro `VITE_` para Vercel.

Para volver a desplegar después de un cambio, se corre el mismo script.

## Variables por servicio

| Servicio | `--use-http2` | Variables |
|---|---|---|
| `ms-pacientes-auth` | sí | `MODULE`, `DATABASE_URL`, `CORS_ORIGINS`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` |
| `ms-ia-recomendacion` | sí | `MODULE`, `DATABASE_URL`, `CORS_ORIGINS`, `GEMINI_API_KEY`, `GEMINI_MODEL` |
| `ms-evaluacion-triaje` | sí | `MODULE`, `DATABASE_URL`, `CORS_ORIGINS`, `GRPC_NEGOTIATION=tls`, `GRPC_CLIENT_PACIENTES`, `GRPC_CLIENT_IA` |
| `ms-cola-espera` | no | `MODULE`, `CORS_ORIGINS`, `GRPC_NEGOTIATION=tls`, `GRPC_CLIENT_EVALUACION` |

`GRPC_CLIENT_*` tiene la forma `static://ms-pacientes-auth-XXXX.us-west1.run.app:443`. `PORT` lo pone Cloud Run.

`DATABASE_URL` es la cadena de Session pooler de Supabase, con la clave en lugar de `[YOUR-PASSWORD]`.

## Costo

Cloud Run cobra por uso y escala a cero. La capa gratuita mensual (2 millones de peticiones, 180.000 vCPU-segundos, 360.000 GiB-segundos) cubre un proyecto de clase. Pide una cuenta de facturación con tarjeta. El script limita cada servicio a una instancia, para no pasar de las conexiones que da el pooler gratis de Supabase.

Artifact Registry da 0,5 GB gratis. Cada build deja una versión de la imagen; si pasas de ese límite, borra las versiones viejas en Artifact Registry.

Un servicio dormido tarda varios segundos en arrancar. La primera petición después de un rato puede tardar hasta un minuto si despierta una cadena de servicios.
