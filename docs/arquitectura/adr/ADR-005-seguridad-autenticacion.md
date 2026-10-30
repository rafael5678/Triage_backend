# ADR-005: Estrategia de Autenticación, Seguridad y Compatibilidad con el Frontend

## Estado
Aceptado

## Contexto
El frontend de TriageIA almacena la sesión del usuario en `sessionStorage` tras invocar `POST /api/v1/auth/login`, retornando un objeto `SessionUserDTO`. El frontend actual no envía encabezados `Authorization: Bearer <token>`. Una imposición rígida de JWT obligatorio en todos los endpoints rompería el frontend inmediatamente, violando la regla 5 del proyecto (*"No modifiques el frontend"*).

## Decisión
Se establece una estrategia de seguridad en dos niveles:
1. **Perímetro de Red:** Los microservicios internos (`ms-*`) no tienen IP pública en Azure ni en Docker Compose; solo el API Gateway puede enviarles tráfico HTTP/gRPC.
2. **Cifrado de Credenciales:** `ms-pacientes-auth` cifra todas las contraseñas con `BCryptPasswordEncoder`.
3. **Compatibilidad:** El Gateway admite la validación de rutas y preserva el contrato actual de `SessionUserDTO`, permitiendo la evolución progresiva hacia tokens JWT firmados sin degradar la experiencia del usuario final.

## Consecuencias
- **Positivas:** Cero interrupción de la aplicación cliente existente y aislamiento seguro de los microservicios de dominio.
- **Negativas:** La autenticación stateless basada estrictamente en JWT requiere una fase posterior de actualización sincronizada con el frontend.

