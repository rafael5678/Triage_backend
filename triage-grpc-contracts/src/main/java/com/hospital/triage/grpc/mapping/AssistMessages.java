package com.hospital.triage.grpc.mapping;

import com.hospital.triage.common.dto.AiAssistDTO;
import com.hospital.triage.grpc.v1.AssistReply;

import java.util.ArrayList;
import java.util.List;

public final class AssistMessages {

    private AssistMessages() {
    }

    public static AssistReply toReply(AiAssistDTO dto) {
        AssistReply.Builder builder = AssistReply.newBuilder()
                .setActive(dto.isActive())
                .setDangerLead(text(dto.getDangerLead()))
                .setMeaning(text(dto.getMeaning()))
                .setSymptomExplanation(text(dto.getSymptomExplanation()))
                .setSuggestedArea(text(dto.getSuggestedArea()))
                .setPhotoFindings(text(dto.getPhotoFindings()))
                .addAllReasons(list(dto.getReasons()))
                .addAllDatasetReasons(list(dto.getDatasetReasons()))
                .addAllGeminiReasons(list(dto.getGeminiReasons()))
                .addAllImmediateActions(list(dto.getImmediateActions()))
                .addAllTestsToOrder(list(dto.getTestsToOrder()))
                .addAllMonitoring(list(dto.getMonitoring()))
                .addAllRedFlags(list(dto.getRedFlags()));
        if (dto.getSuggestedScore() != null) {
            builder.setSuggestedScore(dto.getSuggestedScore());
        }
        if (dto.getProtocolScore() != null) {
            builder.setProtocolScore(dto.getProtocolScore());
        }
        if (dto.getLocalModelScore() != null) {
            builder.setLocalModelScore(dto.getLocalModelScore());
        }
        return builder.build();
    }

    public static AiAssistDTO toDto(AssistReply reply) {
        return AiAssistDTO.builder()
                .active(reply.getActive())
                .suggestedScore(reply.hasSuggestedScore() ? reply.getSuggestedScore() : null)
                .dangerLead(emptyToNull(reply.getDangerLead()))
                .meaning(emptyToNull(reply.getMeaning()))
                .reasons(new ArrayList<>(reply.getReasonsList()))
                .datasetReasons(new ArrayList<>(reply.getDatasetReasonsList()))
                .geminiReasons(new ArrayList<>(reply.getGeminiReasonsList()))
                .protocolScore(reply.hasProtocolScore() ? reply.getProtocolScore() : null)
                .localModelScore(reply.hasLocalModelScore() ? reply.getLocalModelScore() : null)
                .immediateActions(new ArrayList<>(reply.getImmediateActionsList()))
                .testsToOrder(new ArrayList<>(reply.getTestsToOrderList()))
                .monitoring(new ArrayList<>(reply.getMonitoringList()))
                .redFlags(new ArrayList<>(reply.getRedFlagsList()))
                .symptomExplanation(emptyToNull(reply.getSymptomExplanation()))
                .suggestedArea(emptyToNull(reply.getSuggestedArea()))
                .photoFindings(emptyToNull(reply.getPhotoFindings()))
                .build();
    }

    private static List<String> list(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
