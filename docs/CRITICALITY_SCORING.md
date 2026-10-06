# Algoritmo de Cálculo de Criticidad

El score de criticidad es un valor normalizado entre 0 y 100 que pondera las desviaciones vitales:

$$	ext{Score} = w_{spo2} \cdot \Delta_{spo2} + w_{pas} \cdot \Delta_{pas} + w_{fc} \cdot \Delta_{fc} + w_{fr} \cdot \Delta_{fr} + w_{gcs} \cdot \Delta_{gcs} + w_{eva} \cdot 	ext{EVA}$$

Donde cada $\Delta$ representa la penalización por alejamiento de los rangos fisiológicos estables.
