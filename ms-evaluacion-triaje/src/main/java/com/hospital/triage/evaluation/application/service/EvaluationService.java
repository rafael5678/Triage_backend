package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.AnalyzeTriageDTO;
import com.hospital.triage.common.dto.ExtraFindings;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.ReassessmentDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.evaluation.application.port.AssistInput;
import com.hospital.triage.evaluation.application.port.ClinicalAiPort;
import com.hospital.triage.evaluation.application.port.PatientLookupPort;
import com.hospital.triage.evaluation.application.port.PatientSnapshot;
import com.hospital.triage.evaluation.domain.entity.TriageEvaluation;
import com.hospital.triage.evaluation.domain.entity.VitalSigns;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final TriageService triageService;
    private final DestinationService destinations;
    private final ClinicalAiPort clinicalAi;
    private final PatientLookupPort patients;
    private final EvaluationStore store;
    private final ObjectMapper json;

    public RankedPatientDTO evaluate(VitalSignsDTO dto) {
        PatientSnapshot patient = patients.require(dto.getPatientId());
        Scored scored = score(patient.conditionId(), patient.reason(), patient.history(),
                patient.age() == null ? 0 : patient.age(), patient.sex(),
                dto.getHeartRate(), dto.getSpo2(), dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(), dto, null);
        return store.save(patient, dto, scored.result(), scored.ia(), scored.destination());
    }

    public RankedPatientDTO reassess(UUID patientId, ReassessmentDTO dto) {
        VitalSignsDTO vitals = new VitalSignsDTO();
        vitals.setPatientId(patientId);
        vitals.setHeartRate(dto.getHeartRate());
        vitals.setSpo2(dto.getSpo2());
        vitals.setSystolic(dto.getSystolic());
        vitals.setDiastolic(dto.getDiastolic());
        vitals.setTemperature(dto.getTemperature());
        vitals.setRespiratoryRate(dto.getRespiratoryRate());
        vitals.setGlucose(dto.getGlucose());
        vitals.setPainScale(dto.getPainScale());
        vitals.setConsciousness(dto.getConsciousness());
        vitals.setPhotoBase64(dto.getPhotoBase64());
        vitals.setPhotoMime(dto.getPhotoMime());
        vitals.setEstimatedFields(dto.getEstimatedFields());
        return evaluate(vitals);
    }

    public RankedPatientDTO analyze(AnalyzeTriageDTO dto) {
        int age = dto.getAge() == null ? 0 : dto.getAge();
        Scored scored = score(dto.getConditionId(), dto.getReason(), dto.getHistory(), age, dto.getSex(),
                dto.getHeartRate(), dto.getSpo2(), dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(), dto, dto.getLocalModelScore());

        PatientSnapshot preview = new PatientSnapshot(
                null, dto.getName(), dto.getDocument(), dto.getAge(), dto.getSex(),
                dto.getConditionId(), dto.getReason(), dto.getHistory());
        VitalSigns signs = new VitalSigns();
        signs.setHeartRate(dto.getHeartRate());
        signs.setSpo2(dto.getSpo2());
        signs.setSystolic(dto.getSystolic());
        signs.setDiastolic(dto.getDiastolic());
        signs.setTemperature(dto.getTemperature());
        signs.setRespiratoryRate(dto.getRespiratoryRate());
        signs.setGlucose(dto.getGlucose());
        signs.setPainScale(dto.getPainScale());
        signs.setConsciousness(TriageService.avpu(dto.getConsciousness()));
        TriageEvaluation evaluation = new TriageEvaluation();
        evaluation.setRiskScore(scored.result().score());
        evaluation.setTriageLevel(scored.result().level());
        evaluation.setNews2(scored.result().news2());
        evaluation.setEvaluatedAt(OffsetDateTime.now());
        evaluation.setScoreSource(scored.ia().isActive() ? "HYBRID" : "PROTOCOL");
        evaluation.setAiExplanation(RankedPatientAssembler.serializeAi(scored.ia(), json));
        evaluation.setStatus(CareStatus.EN_ESPERA);
        evaluation.setDestinationArea(scored.destination().area().label());
        evaluation.setDestinationCode(scored.destination().code());
        evaluation.setDestinationReason(scored.destination().reason());
        evaluation.setMaxWaitMinutes(scored.destination().maxWaitMinutes());
        evaluation.setEstimatedFields(RankedPatientAssembler.joinFields(dto.getEstimatedFields()));
        return RankedPatientAssembler.toDto(preview, evaluation, signs, null, json);
    }

    public boolean aiAvailable() {
        return clinicalAi.configured();
    }

    public List<RankedPatientDTO> listByStatus(CareStatus status) {
        return enrich(store.loadByStatus(status));
    }

    public List<RankedPatientDTO> listAll() {
        return enrich(store.loadAll());
    }

    public RankedPatientDTO updateStatus(UUID patientId, CareStatus status) {
        RankedPatientDTO updated = store.updateStatus(patientId, status);
        enrich(List.of(updated));
        return updated;
    }

    public void applyPositions(Map<UUID, Integer> positions) {
        store.applyPositions(positions);
    }

    private record Scored(TriageService.RiskResult result, AiAssistDTO ia, DestinationService.Destination destination) {
    }

    private Scored score(String conditionId, String reason, String history, int age, String sex,
                         int heartRate, int spo2, int systolic, int diastolic, BigDecimal temperature, int respiratoryRate,
                         ExtraFindings extra, Double localModelScore) {
        var protocol = triageService.calculate(age, heartRate, spo2, systolic, diastolic, temperature, respiratoryRate,
                conditionId, history, extra.getGlucose(), extra.getPainScale(), extra.getConsciousness());
        AiAssistDTO ia = clinicalAi.assist(AssistInput.builder()
                .conditionId(conditionId)
                .reason(reason)
                .history(history)
                .age(age)
                .sex(sex)
                .heartRate(heartRate)
                .spo2(spo2)
                .systolic(systolic)
                .diastolic(diastolic)
                .temperature(temperature)
                .respiratoryRate(respiratoryRate)
                .glucose(extra.getGlucose())
                .painScale(extra.getPainScale())
                .consciousness(TriageService.avpu(extra.getConsciousness()))
                .estimatedFields(extra.getEstimatedFields())
                .photoBase64(extra.getPhotoBase64())
                .photoMime(extra.getPhotoMime())
                .protocolScore(protocol.score().doubleValue())
                .news2(protocol.news2())
                .localModelScore(localModelScore)
                .build());
        if (ia.getProtocolScore() == null) {
            ia.setProtocolScore(protocol.score().doubleValue());
        }
        var result = triageService.merge(protocol, ia);
        var destination = destinations.decide(result.level(), conditionId, age, ia.getSuggestedArea());
        return new Scored(result, ia, destination);
    }

    private List<RankedPatientDTO> enrich(List<RankedPatientDTO> rows) {
        List<UUID> ids = new ArrayList<>();
        for (RankedPatientDTO row : rows) {
            if (row.getPatientId() != null) {
                ids.add(row.getPatientId());
            }
        }
        Map<UUID, PatientSnapshot> people = patients.findAll(ids);
        for (RankedPatientDTO row : rows) {
            PatientSnapshot person = people.getOrDefault(row.getPatientId(), PatientSnapshot.unknown(row.getPatientId()));
            row.setName(person.name());
            row.setDocument(person.document());
            row.setAge(person.age());
            row.setSex(person.sex());
            row.setConditionId(person.conditionId());
            row.setReason(person.reason());
            row.setHistory(person.history());
        }
        return rows;
    }
}
