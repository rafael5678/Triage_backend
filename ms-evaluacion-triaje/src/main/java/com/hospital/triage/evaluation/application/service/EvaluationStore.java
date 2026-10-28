package com.hospital.triage.evaluation.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.common.exception.PatientNotFoundException;
import com.hospital.triage.evaluation.application.port.PatientSnapshot;
import com.hospital.triage.evaluation.domain.entity.TriageEvaluation;
import com.hospital.triage.evaluation.domain.entity.VitalSigns;
import com.hospital.triage.evaluation.infrastructure.persistence.TriageEvaluationRepository;
import com.hospital.triage.evaluation.infrastructure.persistence.VitalSignsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cada medición crea una evaluación nueva; la anterior deja de ser vigente y queda como historial.
 * La cola y las estadísticas solo leen evaluaciones vigentes.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EvaluationStore {

    private final VitalSignsRepository vitalSignsRepo;
    private final TriageEvaluationRepository evaluations;
    private final ObjectMapper json;

    public RankedPatientDTO save(PatientSnapshot patient, VitalSignsDTO dto, TriageService.RiskResult result, AiAssistDTO ia,
                                 DestinationService.Destination destination) {
        VitalSigns signs = new VitalSigns();
        signs.setPatientId(patient.id());
        signs.setHeartRate(dto.getHeartRate());
        signs.setSpo2(dto.getSpo2());
        signs.setSystolic(dto.getSystolic());
        signs.setDiastolic(dto.getDiastolic());
        signs.setTemperature(dto.getTemperature());
        signs.setRespiratoryRate(dto.getRespiratoryRate());
        signs.setGlucose(dto.getGlucose());
        signs.setPainScale(dto.getPainScale());
        signs.setConsciousness(TriageService.avpu(dto.getConsciousness()));
        signs.setRecordedAt(OffsetDateTime.now());
        vitalSignsRepo.save(signs);

        TriageEvaluation evaluation = new TriageEvaluation();
        Optional<TriageEvaluation> previous = evaluations.findTopByPatientIdAndCurrentTrueOrderByEvaluatedAtDesc(patient.id());
        previous.ifPresent(old -> carryOver(old, evaluation));
        if (dto.getPresent() != null) {
            evaluation.setPresent(dto.getPresent());
        }
        evaluation.setPatientId(patient.id());
        evaluation.setVitalSigns(signs);
        evaluation.setRiskScore(result.score());
        evaluation.setTriageLevel(result.level());
        evaluation.setNews2(result.news2());
        evaluation.setEvaluatedAt(OffsetDateTime.now());
        evaluation.setEvaluatedBy(truncate(dto.getEvaluatedBy(), 120));
        evaluation.setScoreSource(ia.isActive() ? "HYBRID" : "PROTOCOL");
        evaluation.setAiExplanation(RankedPatientAssembler.serializeAi(ia, json));
        evaluation.setDestinationArea(destination.area().label());
        evaluation.setDestinationCode(destination.code());
        evaluation.setDestinationReason(truncate(destination.reason(), 300));
        evaluation.setMaxWaitMinutes(destination.maxWaitMinutes());
        evaluation.setEstimatedFields(RankedPatientAssembler.joinFields(dto.getEstimatedFields()));
        if (evaluation.getArrivedAt() == null) {
            evaluation.setArrivedAt(evaluation.getEvaluatedAt());
        }
        evaluation.setCurrent(true);
        evaluations.save(evaluation);
        return RankedPatientAssembler.toDto(patient, evaluation, signs, evaluation.getQueuePosition(), json);
    }

    /**
     * Si sigue en la misma visita (en espera o en atención) conserva estado, presencia y aviso;
     * si la visita anterior ya terminó, empieza una nueva en espera.
     */
    static void carryOver(TriageEvaluation old, TriageEvaluation next) {
        old.setCurrent(false);
        boolean sameVisit = old.getStatus() == CareStatus.EN_ESPERA || old.getStatus() == CareStatus.EN_ATENCION;
        if (!sameVisit) {
            next.setStatus(CareStatus.EN_ESPERA);
            return;
        }
        next.setStatus(old.getStatus());
        next.setArrivedAt(old.getArrivedAt() != null ? old.getArrivedAt() : old.getEvaluatedAt());
        next.setQueuePosition(old.getQueuePosition());
        next.setCareStartedAt(old.getCareStartedAt());
        next.setPresent(old.isPresent());
        next.setNotifiedAt(old.getNotifiedAt());
        next.setNotifiedChannel(old.getNotifiedChannel());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @Transactional(readOnly = true)
    public List<RankedPatientDTO> loadByStatus(CareStatus status) {
        return toClinical(evaluations.findByStatusAndCurrentTrueOrderByRiskScoreDescEvaluatedAtAsc(status));
    }

    @Transactional(readOnly = true)
    public List<RankedPatientDTO> loadAll() {
        return toClinical(evaluations.findByCurrentTrueOrderByEvaluatedAtDesc());
    }

    /** Todas las evaluaciones del paciente, de la más antigua a la más reciente. */
    @Transactional(readOnly = true)
    public List<RankedPatientDTO> history(UUID patientId) {
        return toClinical(evaluations.findByPatientIdOrderByEvaluatedAtAsc(patientId));
    }

    public RankedPatientDTO updateStatus(UUID patientId, CareStatus newStatus) {
        TriageEvaluation evaluation = current(patientId);
        evaluation.setStatus(newStatus);
        if (newStatus != CareStatus.EN_ESPERA) {
            evaluation.setQueuePosition(null);
        }
        if (newStatus == CareStatus.EN_ATENCION && evaluation.getCareStartedAt() == null) {
            evaluation.setCareStartedAt(OffsetDateTime.now());
        }
        evaluations.save(evaluation);
        return toDto(evaluation);
    }

    public RankedPatientDTO markNotified(UUID patientId, String channel) {
        TriageEvaluation evaluation = current(patientId);
        evaluation.setNotifiedAt(OffsetDateTime.now());
        evaluation.setNotifiedChannel(truncate(channel == null || channel.isBlank() ? "MANUAL" : channel.trim().toUpperCase(), 20));
        evaluations.save(evaluation);
        return toDto(evaluation);
    }

    public RankedPatientDTO setPresence(UUID patientId, boolean present) {
        TriageEvaluation evaluation = current(patientId);
        evaluation.setPresent(present);
        evaluations.save(evaluation);
        return toDto(evaluation);
    }

    public void applyPositions(Map<UUID, Integer> positions) {
        for (Map.Entry<UUID, Integer> entry : positions.entrySet()) {
            evaluations.findTopByPatientIdAndCurrentTrueOrderByEvaluatedAtDesc(entry.getKey()).ifPresent(evaluation -> {
                if (evaluation.getStatus() == CareStatus.EN_ESPERA && !entry.getValue().equals(evaluation.getQueuePosition())) {
                    evaluation.setQueuePosition(entry.getValue());
                    evaluations.save(evaluation);
                }
            });
        }
    }

    private TriageEvaluation current(UUID patientId) {
        return evaluations.findTopByPatientIdAndCurrentTrueOrderByEvaluatedAtDesc(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId.toString()));
    }

    private RankedPatientDTO toDto(TriageEvaluation evaluation) {
        VitalSigns signs = evaluation.getVitalSigns();
        if (signs != null) {
            signs.getHeartRate();
        }
        return RankedPatientAssembler.toDto(PatientSnapshot.unknown(evaluation.getPatientId()), evaluation, signs,
                evaluation.getQueuePosition(), json);
    }

    private List<RankedPatientDTO> toClinical(List<TriageEvaluation> rows) {
        List<RankedPatientDTO> out = new ArrayList<>();
        for (TriageEvaluation evaluation : rows) {
            out.add(toDto(evaluation));
        }
        return out;
    }
}
