package com.hospital.triage.evaluation.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PatientLookupPort {
    PatientSnapshot require(UUID id);

    Map<UUID, PatientSnapshot> findAll(Collection<UUID> ids);
}
