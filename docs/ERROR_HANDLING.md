# Manejo Global de Errores

Gestionado por `@RestControllerAdvice` en `GlobalExceptionHandler.java`:

- `PacienteNoEncontradoException`: Devuelve HTTP 404 con mensaje estructurado.
- `LecturaSignosInvalidosException`: Devuelve HTTP 400 detallando el campo fuera de rango.
- `MethodArgumentNotValidException`: Captura errores de validación de Bean Validation.
- `Exception`: Handler genérico para errores 500 no previstos.
