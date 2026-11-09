/** SymptomSeverityPort: application type for the triage hospital services. */
package com.hospital.triage.evaluation.application.port;

import java.util.Optional;

public interface SymptomSeverityPort {
    Optional<Integer> severityOf(String conditionId);
}
