# Mitigación de Vulnerabilidades de Persistencia

- Uso exclusivo de consultas preparadas mediante Spring Data JPA e Hibernate.
- Prohibición de concatenación de cadenas en queries nativas.
- Validación de parámetros de ordenación en la cola dinámica contra listas blancas.
