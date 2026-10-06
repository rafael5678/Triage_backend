# Plan de Contingencia y Recuperación

En caso de indisponibilidad temporal de la base de datos principal:

1. El microservicio mantiene en memoria caché volátil las últimas 50 evaluaciones activas.
2. Se emite alarma visual al frontend.
3. Al restaurarse el pool de conexiones, las transacciones pendientes son sincronizadas.
