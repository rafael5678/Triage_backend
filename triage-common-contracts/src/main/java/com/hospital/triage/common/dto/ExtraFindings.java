package com.hospital.triage.common.dto;

import java.util.List;

/**
 * Optional intake fields shared by analysis, evaluation and reassessment.
 * consciousness uses AVPU: A alert, C new confusion, V voice, P pain, U unresponsive.
 */
public interface ExtraFindings {
    Integer getGlucose();

    Integer getPainScale();

    String getConsciousness();

    String getPhotoBase64();

    String getPhotoMime();

    List<String> getEstimatedFields();

    /** 0-100 severity of the catalog symptom (set by administration). */
    default Integer getConditionSeverity() {
        return null;
    }
}
