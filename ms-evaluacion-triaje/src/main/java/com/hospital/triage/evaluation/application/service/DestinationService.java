package com.hospital.triage.evaluation.application.service;

import com.hospital.triage.common.enums.CareArea;
import com.hospital.triage.common.enums.TriageLevel;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Decide a qué área de urgencias va el paciente y en cuánto tiempo debe atenderse.
 * El nivel de triaje fija el área base; algunos motivos activan un código institucional
 * con un área mínima. La sugerencia de la IA solo puede subir de área, nunca bajar.
 */
@Service
public class DestinationService {

    public record Destination(CareArea area, String code, String reason, int maxWaitMinutes) {
    }

    private record Code(String name, CareArea minimumArea, String why) {
    }

    private static final Map<String, Code> CODES = Map.of(
            "paro", new Code("Código azul", CareArea.REANIMACION, "posible paro: reanimación inmediata"),
            "avc", new Code("Código ACV", CareArea.URGENCIAS_CRITICAS, "posible derrame: cada minuto cuenta para el tratamiento"),
            "sca", new Code("Código infarto", CareArea.URGENCIAS_CRITICAS, "posible infarto: ECG en menos de 10 minutos"),
            "trauma", new Code("Código trauma", CareArea.URGENCIAS_CRITICAS, "trauma fuerte: valoración primaria inmediata"),
            "sepsis", new Code("Código sepsis", CareArea.URGENCIAS_CRITICAS, "posible sepsis: antibiótico en la primera hora"),
            "anafilaxia", new Code("Código anafilaxia", CareArea.URGENCIAS_CRITICAS, "alergia grave: adrenalina disponible"),
            "hemorragia", new Code("Código rojo", CareArea.URGENCIAS_CRITICAS, "sangrado activo: controlar y reponer volumen")
    );

    public Destination decide(TriageLevel level, String conditionId, Integer age, String aiSuggestedArea) {
        return decide(level, conditionId, age, aiSuggestedArea, false, null, 0, 0);
    }

    /** Con embarazo, la presión muy alta o el sangrado activan un código obstétrico propio. */
    public Destination decide(TriageLevel level, String conditionId, Integer age, String aiSuggestedArea,
                              boolean pregnant, Integer gestationWeeks, int systolic, int diastolic) {
        CareArea area = CareArea.fromLevel(level);
        StringBuilder reason = new StringBuilder("Nivel de triaje: ").append(area.label());
        String code = null;

        Code special = conditionId == null ? null : CODES.get(conditionId);
        if (pregnant) {
            Code obstetric = obstetricCode(conditionId, gestationWeeks, systolic, diastolic);
            if (obstetric != null) {
                special = obstetric;
            }
        }
        if (special != null) {
            code = special.name();
            if (special.minimumArea().moreUrgentThan(area)) {
                area = special.minimumArea();
            }
            reason.append(". ").append(capitalize(special.why()));
        }

        CareArea suggested = CareArea.parse(aiSuggestedArea);
        if (suggested != null && suggested.moreUrgentThan(area)) {
            area = suggested;
            reason.append(". La IA lo subió a ").append(suggested.label());
        }

        if (age != null && age < 14) {
            code = code == null ? "Pediatría" : code + " · Pediatría";
            reason.append(". Menor de 14 años: atención pediátrica");
        }
        if (pregnant && (code == null || !code.contains("obst") && !code.contains("eclampsia"))) {
            code = code == null ? "Obstetricia" : code + " · Obstetricia";
            reason.append(". Embarazada").append(gestationWeeks == null ? "" : " de " + gestationWeeks + " semanas")
                    .append(": valoración obstétrica");
        }
        return new Destination(area, code, reason.toString(), area.maxWaitMinutes());
    }

    private static Code obstetricCode(String conditionId, Integer gestationWeeks, int systolic, int diastolic) {
        boolean afterTwenty = gestationWeeks == null || gestationWeeks >= 20;
        if (afterTwenty && (systolic >= 160 || diastolic >= 110)) {
            return new Code("Código preeclampsia", CareArea.URGENCIAS_CRITICAS,
                    "presión muy alta en el embarazo: riesgo de preeclampsia severa y convulsión");
        }
        if ("hemorragia".equals(conditionId)) {
            return new Code("Código rojo obstétrico", CareArea.URGENCIAS_CRITICAS,
                    "sangrado en el embarazo: riesgo para la madre y el bebé");
        }
        if (afterTwenty && "convulsion".equals(conditionId)) {
            return new Code("Código eclampsia", CareArea.REANIMACION, "convulsión en el embarazo: posible eclampsia");
        }
        return null;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
