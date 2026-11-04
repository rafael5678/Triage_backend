package com.hospital.triage.common.dto;

import java.util.List;

/**
 * Datos opcionales del ingreso que comparten el análisis, la evaluación y la reevaluación.
 * consciousness usa la escala AVPU: A alerta, C confusión nueva, V responde a la voz, P al dolor, U no responde.
 */
public interface ExtraFindings {
    Integer getGlucose();

    Integer getPainScale();

    String getConsciousness();

    String getPhotoBase64();

    String getPhotoMime();

    List<String> getEstimatedFields();

    /** Gravedad 0-100 del síntoma en el catálogo del hospital (la que definió administración). */
    default Integer getConditionSeverity() {
        return null;
    }
}
