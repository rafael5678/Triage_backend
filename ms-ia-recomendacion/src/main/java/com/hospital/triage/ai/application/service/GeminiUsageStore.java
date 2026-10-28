package com.hospital.triage.ai.application.service;

import java.time.LocalDate;

/** Cuenta las llamadas a Gemini por día en un lugar que sobrevive a los reinicios del servicio. */
public interface GeminiUsageStore {
    /** Suma una llamada al día y devuelve el total del día ya incluida esta. */
    int incrementAndGet(LocalDate day);

    int get(LocalDate day);
}
