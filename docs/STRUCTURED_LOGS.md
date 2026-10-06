# Formato de Logs Estructurados

En entornos productivos, los logs se emiten en formato JSON:

```json
{
  "timestamp": "2026-10-06T00:15:00Z",
  "level": "INFO",
  "service": "triage-backend",
  "action": "EVALUACION_CREADA",
  "pacienteId": 142,
  "nivel": "NIVEL_II",
  "score": 78.4
}
```
