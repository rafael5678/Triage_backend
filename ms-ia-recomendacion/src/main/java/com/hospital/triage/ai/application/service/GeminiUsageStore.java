package com.hospital.triage.ai.application.service;

import java.time.LocalDate;

/** Counts Gemini calls per day in a store that survives service restarts. */
public interface GeminiUsageStore {
    /** Adds one call for the day and returns the day's total including this one. */
    int incrementAndGet(LocalDate day);

    int get(LocalDate day);
}
