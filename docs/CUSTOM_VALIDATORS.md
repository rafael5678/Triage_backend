# Validadores Clínicos Personalizados

Implementación de anotaciones personalizadas sobre DTOs:

- `@PresionCoherente`: Valida que la Presión Arterial Sistólica sea estrictamente mayor que la Diastólica.
- `@RangoTemperatura`: Asegura lecturas fisiológicamente compatibles con la vida.
- Reduce la duplicación de código en la capa de controlador.
