/** ClinicalAiPort: application type for the triage hospital services. */
package com.hospital.triage.evaluation.application.port;

import com.hospital.triage.common.dto.AiAssistDTO;

public interface ClinicalAiPort {
    AiAssistDTO assist(AssistInput input);

    boolean configured();
}
