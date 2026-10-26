package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.common.dto.AnalyzeTriageDTO;
import com.hospital.triage.common.dto.RankedPatientDTO;
import com.hospital.triage.common.dto.ReassessmentDTO;
import com.hospital.triage.common.dto.VitalSignsDTO;
import com.hospital.triage.common.enums.CareStatus;
import com.hospital.triage.evaluation.application.port.ClinicalAiPort;
import com.hospital.triage.evaluation.application.port.PatientLookupPort;
import com.hospital.triage.evaluation.application.port.PatientSnapshot;
import com.hospital.triage.evaluation.domain.entity.TriageEvaluation;
import com.hospital.triage.evaluation.domain.entity.VitalSigns;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final TriageService triageService;
    private final ClinicalAiPort clinicalAi;
    private final PatientLookupPort patients;
    private final EvaluationStore store;
    private final ObjectMapper json;

    public RankedPatientDTO evaluate(VitalSignsDTO dto) {
        PatientSnapshot patient = patients.require(dto.getPatientId());
        int age = patient.age() == null ? 0 : patient.age();
        var protocol = triageService.calculate(
                age, dto.getHeartRate(), dto.getSpo2(),
                dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                patient.conditionId(), patient.history()
        );
        AiAssistDTO ia = assist(patient.conditionId(), patient.reason(), patient.history(), age, patient.sex(), dto, protocol);
        return store.save(patient, dto, triageService.merge(protocol, ia), ia);
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
        return evaluate(vitals);
    }

    public RankedPatientDTO analyze(AnalyzeTriageDTO dto) {
        int age = dto.getAge() == null ? 0 : dto.getAge();
        var protocol = triageService.calculate(
                age, dto.getHeartRate(), dto.getSpo2(),
                dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                dto.getConditionId(), dto.getHistory()
        );
        AiAssistDTO ia = clinicalAi.assist(
                dto.getConditionId(), dto.getReason(), dto.getHistory(), age, dto.getSex(),
                dto.getHeartRate(), dto.getSpo2(), dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                protocol.score().doubleValue(), protocol.news2(), dto.getLocalModelScore()
        );
        if (ia.getProtocolScore() == null) {
            ia.setProtocolScore(protocol.score().doubleValue());
        }
        var result = triageService.merge(protocol, ia);

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
        TriageEvaluation evaluation = new TriageEvaluation();
        evaluation.setRiskScore(result.score());
        evaluation.setTriageLevel(result.level());
        evaluation.setNews2(result.news2());
        evaluation.setEvaluatedAt(OffsetDateTime.now());
        evaluation.setScoreSource(ia.isActive() ? "HYBRID" : "PROTOCOL");
        evaluation.setAiExplanation(RankedPatientAssembler.serializeAi(ia, json));
        evaluation.setStatus(CareStatus.EN_ESPERA);
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

    private AiAssistDTO assist(String conditionId, String reason, String history, int age, String sex,
                               VitalSignsDTO dto, TriageService.RiskResult protocol) {
        AiAssistDTO ia = clinicalAi.assist(
                conditionId, reason, history, age, sex,
                dto.getHeartRate(), dto.getSpo2(), dto.getSystolic(), dto.getDiastolic(),
                dto.getTemperature(), dto.getRespiratoryRate(),
                protocol.score().doubleValue(), protocol.news2(), null
        );
        if (ia.getProtocolScore() == null) {
            ia.setProtocolScore(protocol.score().doubleValue());
        }
        return ia;
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
