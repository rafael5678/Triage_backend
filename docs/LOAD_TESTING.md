# Pruebas de Carga y Concurrencia

Resultados de pruebas simuladas de estrés:

- **Carga Soportada**: 250 solicitudes concurrentes por segundo con latencia inferior a 120 ms.
- **Consumo de Memoria JVM**: Estable en ~320 MB de memoria heap.
- **Sin fugas de conexiones** en el pool de base de datos tras 10,000 transacciones consecutivas.
