# Estrategia de Pruebas y Cobertura de Calidad — TriageIA

## 1. Pirámide de Pruebas
La arquitectura cuenta con una suite automatizada de pruebas ejecutada con **JUnit 5**, **Mockito**, **AssertJ** y **Testcontainers**:

```
        / \
       / E2E \       <-- Flujo a través de API Gateway
      /-------\
     / Integ.  \     <-- gRPC over HTTP, Testcontainers Postgres
    /-----------\
   /   Unitarias \   <-- Reglas Clínicas, NEWS2, Pediatría, Gemini Fallback
  /---------------\
```

---

## 2. Resumen de la Suite de Pruebas Actual (49 Pruebas Pasando)

| Módulo | Pruebas | Cobertura Crítica |
|---|---|---|
| `api-gateway` | 1 | Carga de contexto reactivo, inicialización de rutas y filtros. |
| `legacy-monolith` | 13 | Pruebas de compatibilidad del motor original. |
| `ms-pacientes-auth` | 3 | Multiplexación gRPC sobre HTTP/2 h2c y validación de usuarios. |
| `ms-evaluacion-triaje` | 13 | **Protección de Reglas Clínicas:** `ClinicalRulesTest` (NEWS2, umbrales pediátricos, preeclampsia, eclampsia, shock), `DestinationServiceTest`, `GrpcMappingTest`. |
| `ms-cola-espera` | 8 | Priorización estricta por score y llegada, cálculo de espera, notificaciones. |
| `ms-ia-recomendacion` | 11 | Resiliencia de Gemini, manejo de errores 503, tope de cuotas y modo fallback. |

---

## 3. Ejecución de Pruebas

### Pruebas Unitarias Rápidas:
```bash
mvn test
```

### Ejecutar Pruebas de un Solo Módulo:
```bash
mvn test -pl ms-evaluacion-triaje
```

### Pruebas de Reglas Clínicas Exclusivamente:
```bash
mvn test -pl ms-evaluacion-triaje -Dtest=ClinicalRulesTest
```

