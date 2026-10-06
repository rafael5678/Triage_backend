# Estrategia de Pruebas

1. **Pruebas Unitarias de Lógica (`TriageServiceTest`)**:
   - Valida la asignación matemática del score Manchester frente a casos borde.
2. **Pruebas de Repositorio (`@DataJpaTest`)**:
   - Verifica queries complejas de ordenación de cola contra base de datos en memoria H2.
3. **Pruebas de Integración Web (`@WebMvcTest`)**:
   - Comprueba deserialización JSON de DTOs y mapeo de excepciones.
