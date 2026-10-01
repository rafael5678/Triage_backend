package com.hospital.triage.application.service;

import com.hospital.triage.application.dto.EstadoColaTriageDTO;
import com.hospital.triage.application.dto.IngresoPacienteDTO;
import com.hospital.triage.application.dto.PacientePriorizadoDTO;
import com.hospital.triage.application.dto.ReevaluacionDTO;
import com.hospital.triage.application.dto.RegistroSignosVitalesDTO;
import com.hospital.triage.domain.enums.EstadoAtencion;
import com.hospital.triage.domain.exception.PacienteNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cola en memoria ordenada por score descendente y timestamp de llegada.
 * Cuando se reevalúa, se recalcula el score y se reordena de inmediato.
 */
@Service
@RequiredArgsConstructor
public class ColaAtencionService {

    private final TriageService triageService;
    private final Map<UUID, PacientePriorizadoDTO> enSala = new ConcurrentHashMap<>();
    private final Map<UUID, IngresoPacienteDTO> fichas = new ConcurrentHashMap<>();

    public PacientePriorizadoDTO registrarPaciente(IngresoPacienteDTO dto) {
        UUID id = UUID.randomUUID();
        fichas.put(id, dto);
        PacientePriorizadoDTO item = PacientePriorizadoDTO.builder()
                .pacienteId(id)
                .nombre(dto.getNombre())
                .documento(dto.getDocumento())
                .edad(dto.getEdad())
                .lesionId(dto.getLesionId())
                .scoreRiesgo(BigDecimal.ZERO)
                .estado(EstadoAtencion.EN_ESPERA)
                .evaluadoEn(OffsetDateTime.now())
                .build();
        enSala.put(id, item);
        return item;
    }

    public PacientePriorizadoDTO evaluar(RegistroSignosVitalesDTO dto) {
        PacientePriorizadoDTO actual = require(dto.getPacienteId());
        IngresoPacienteDTO ficha = fichas.get(dto.getPacienteId());
        var r = triageService.calcular(
                actual.getEdad(),
                dto.getFrecuenciaCardiaca(),
                dto.getSpo2(),
                dto.getPresionSistolica(),
                dto.getPresionDiastolica(),
                dto.getTemperatura(),
                dto.getFrecuenciaRespiratoria(),
                actual.getLesionId(),
                ficha != null ? ficha.getAntecedentes() : null
        );
        PacientePriorizadoDTO next = copy(actual, r);
        enSala.put(next.getPacienteId(), next);
        return withPositions().stream()
                .filter(p -> p.getPacienteId().equals(next.getPacienteId()))
                .findFirst()
                .orElse(next);
    }

    public PacientePriorizadoDTO reevaluar(UUID pacienteId, ReevaluacionDTO dto) {
        RegistroSignosVitalesDTO signos = new RegistroSignosVitalesDTO();
        signos.setPacienteId(pacienteId);
        signos.setFrecuenciaCardiaca(dto.getFrecuenciaCardiaca());
        signos.setSpo2(dto.getSpo2());
        signos.setPresionSistolica(dto.getPresionSistolica());
        signos.setPresionDiastolica(dto.getPresionDiastolica());
        signos.setTemperatura(dto.getTemperatura());
        signos.setFrecuenciaRespiratoria(dto.getFrecuenciaRespiratoria());
        return evaluar(signos);
    }

    public PacientePriorizadoDTO atender(UUID pacienteId) {
        PacientePriorizadoDTO actual = require(pacienteId);
        PacientePriorizadoDTO next = PacientePriorizadoDTO.builder()
                .pacienteId(actual.getPacienteId())
                .nombre(actual.getNombre())
                .documento(actual.getDocumento())
                .edad(actual.getEdad())
                .lesionId(actual.getLesionId())
                .scoreRiesgo(actual.getScoreRiesgo())
                .nivelTriage(actual.getNivelTriage())
                .estado(EstadoAtencion.EN_ATENCION)
                .posicionCola(null)
                .news2(actual.getNews2())
                .evaluadoEn(OffsetDateTime.now())
                .build();
        enSala.put(pacienteId, next);
        return next;
    }

    public EstadoColaTriageDTO cola() {
        List<PacientePriorizadoDTO> lista = withPositions();
        return EstadoColaTriageDTO.builder()
                .generadoEn(OffsetDateTime.now())
                .totalEnEspera(lista.size())
                .cola(lista)
                .build();
    }

    private List<PacientePriorizadoDTO> withPositions() {
        List<PacientePriorizadoDTO> espera = enSala.values().stream()
                .filter(p -> p.getEstado() == EstadoAtencion.EN_ESPERA)
                .sorted(Comparator
                        .comparing(PacientePriorizadoDTO::getScoreRiesgo, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PacientePriorizadoDTO::getEvaluadoEn))
                .toList();
        int i = 1;
        List<PacientePriorizadoDTO> ranked = new java.util.ArrayList<>();
        for (PacientePriorizadoDTO p : espera) {
            ranked.add(PacientePriorizadoDTO.builder()
                    .pacienteId(p.getPacienteId())
                    .nombre(p.getNombre())
                    .documento(p.getDocumento())
                    .edad(p.getEdad())
                    .lesionId(p.getLesionId())
                    .scoreRiesgo(p.getScoreRiesgo())
                    .nivelTriage(p.getNivelTriage())
                    .estado(p.getEstado())
                    .posicionCola(i++)
                    .news2(p.getNews2())
                    .evaluadoEn(p.getEvaluadoEn())
                    .build());
        }
        return ranked;
    }

    private PacientePriorizadoDTO copy(PacientePriorizadoDTO actual, TriageService.ResultadoCriticidad r) {
        return PacientePriorizadoDTO.builder()
                .pacienteId(actual.getPacienteId())
                .nombre(actual.getNombre())
                .documento(actual.getDocumento())
                .edad(actual.getEdad())
                .lesionId(actual.getLesionId())
                .scoreRiesgo(r.score())
                .nivelTriage(r.nivel())
                .estado(EstadoAtencion.EN_ESPERA)
                .news2(r.news2())
                .evaluadoEn(OffsetDateTime.now())
                .build();
    }

    private PacientePriorizadoDTO require(UUID id) {
        PacientePriorizadoDTO p = enSala.get(id);
        if (p == null) {
            throw new PacienteNoEncontradoException(id.toString());
        }
        return p;
    }
}
