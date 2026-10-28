# Eventos en vivo y avisos por WhatsApp

## Eventos en vivo (SSE)

`ms-cola-espera` expone `GET /api/v1/triage/events` (`text/event-stream`).

- Al conectarse llega el evento `hola`.
- Cuando cambia la sala (alguien nuevo, una reevaluación, pasa a atención, presencia o aviso) llega `cambio`.
  El evento no lleva datos de pacientes: cada pantalla vuelve a pedir `/patients`, `/board` o `/stats`.
- Mientras haya al menos una pantalla conectada, la cola revisa cada 5 s; sin pantallas no hace nada.
- Cada 25 s se manda un comentario de latido para que el ingreso de Azure no corte la conexión.
- El frontend cierra la conexión cuando la pestaña queda oculta.

> En el plan gratuito de Azure los servicios se apagan cuando nadie los usa. Una pantalla abierta
> (por ejemplo el televisor de la sala) los mantiene encendidos y gasta crédito de Azure for Students.
> Apague la pantalla cuando no se use.

## Cuándo se avisa a un paciente

Solo a pacientes `EN_ESPERA`, con celular y sin aviso previo en esta visita, si se cumple alguna:

| Regla | Valor por defecto | Variable |
|---|---|---|
| Faltan pocos minutos para su tiempo máximo | 10 min | `WHATSAPP_LEAD_MINUTES` |
| Está entre los primeros de la fila | puesto 2 | `WHATSAPP_POSITION_LEAD` |

- Si está **en la sala**: "Su turno en {área} está cerca (puesto N). Permanezca en la sala".
- Si se registró pero **no ha llegado** (`presente = false`): "Su turno está por vencerse y aún no registramos su llegada.
  Preséntese en los próximos N minutos para conservar su prioridad".
- La espera se mide desde `llegada_en`, que no cambia aunque lo reevalúen.
- El aviso queda en `evaluaciones_triage.notificado_en` y `canal_aviso` (`WHATSAPP` automático o `MANUAL`).

Los avisos automáticos se revisan cuando alguien consulta la cola o hay una pantalla en vivo abierta.
Con el servicio apagado no se envía nada; en ese caso el personal usa el botón **Avisar por WhatsApp**,
que abre `wa.me` con el mismo mensaje y registra el aviso como `MANUAL`.

## Configurar WhatsApp Cloud API (Meta)

1. En <https://developers.facebook.com/apps> cree una app de tipo **Empresa** y agregue el producto **WhatsApp**.
2. En *WhatsApp → Configuración de la API* copie el **Identificador del número de teléfono** (Phone number ID).
3. En modo de prueba, agregue en "Para" los celulares que recibirán avisos (Meta solo permite números verificados).
4. Cree un token permanente: *Configuración del negocio → Usuarios del sistema → Generar token* con el permiso
   `whatsapp_business_messaging`.
5. (Recomendado) Cree una plantilla de categoría **Utilidad** en español con tres variables, por ejemplo:

   ```
   Hola {{1}}, le escribimos de Urgencias. Sobre su turno en {{2}}: {{3}}.
   ```

   Sin plantilla se envía texto libre, pero Meta solo lo entrega si el paciente escribió al número en las últimas 24 h.

6. Cargue los valores en Azure **escribiéndolos directamente en la terminal de Azure, nunca en el chat**:

   ```bash
   az containerapp secret set -n ms-cola-espera -g triage-brazilsouth \
     --secrets whatsapp-token=<TOKEN>
   az containerapp update -n ms-cola-espera -g triage-brazilsouth \
     --set-env-vars WHATSAPP_TOKEN=secretref:whatsapp-token WHATSAPP_PHONE_ID=<PHONE_NUMBER_ID> \
                    WHATSAPP_TEMPLATE=<nombre_plantilla> WHATSAPP_TEMPLATE_LANG=es
   ```

7. Compruebe en `GET /api/v1/triage/health` de la cola que diga `"whatsapp": "automatico"`.
