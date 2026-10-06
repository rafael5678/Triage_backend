# Gestión Dinámica de la Cola de Urgencias

La ordenación de atención no sigue un esquema estricto FIFO (primero en llegar, primero en ser atendido), sino una función de prioridad ponderada:

$$	ext{Prioridad} = 	ext{PesoNivel} 	imes 1000 + 	ext{ScoreCriticidad} + (	ext{MinutosEspera} 	imes lpha)$$

Esto evita el fenómeno de *inanición* en pacientes de nivel intermedio con tiempos prolongados de espera.
