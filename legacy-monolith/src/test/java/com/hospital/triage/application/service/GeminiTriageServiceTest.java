package com.hospital.triage.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.application.dto.AiAssistDTO;
import com.hospital.triage.infrastructure.persistence.SymptomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiTriageServiceTest {

    @Mock
    private SymptomRepository symptoms;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private GeminiTriageService geminiService;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiTriageService(new ObjectMapper(), symptoms);
    }

    @Test
    @DisplayName("TC-11: API key de Gemini ausente o vacía -> Fallback silencioso sin invocar HTTP")
    void tc11_apiKeyAusente() {
        // Por defecto apiKey es null o vacía
        ReflectionTestUtils.setField(geminiService, "apiKey", "");

        AiAssistDTO result = geminiService.assist(
                "cefalea", "Dolor leve", "", 35, "M",
                75, 98, 120, 80, BigDecimal.valueOf(36.5), 16,
                20.0, 0, null
        );

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isFalse();
        assertThat(result.getSuggestedScore()).isNull();
    }

    @Test
    @DisplayName("TC-12: Error HTTP, excepción o timeout de Gemini -> Captura excepción y retorna active=false")
    void tc12_errorHttpOTimeoutGemini() throws Exception {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiService, "http", mockHttpClient);
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());

        // Simular excepción de red / timeout en la llamada HTTP
        when(mockHttpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Simulated timeout connection to Google Gemini API"));

        AiAssistDTO result = geminiService.assist(
                "disnea", "Falta de aire", "epoc", 65, "M",
                110, 88, 130, 85, BigDecimal.valueOf(37.2), 26,
                75.0, 5, null
        );

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isFalse();
        assertThat(result.getSuggestedScore()).isNull();
    }

    @Test
    @DisplayName("TC-13: Respuesta JSON válida de Gemini -> Parsing correcto de score, dangerLead y razones")
    void tc13_respuestaValidaGemini() throws Exception {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiService, "http", mockHttpClient);
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());

        String geminiApiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"score\\": 85.0, \\"dangerLead\\": \\"Sospecha alta de SCA\\", \\"meaning\\": \\"Prioridad máxima en sala\\", \\"fromDataset\\": [\\"FC elevada\\"], \\"fromGemini\\": [\\"Dolor torácico irradiado a mandíbula\\"]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(geminiApiResponse);
        org.mockito.Mockito.doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

        AiAssistDTO result = geminiService.assist(
                "sca", "Dolor de pecho opresivo", "hta", 58, "M",
                115, 93, 140, 90, BigDecimal.valueOf(36.8), 22,
                68.0, 3, null
        );

        assertThat(result).isNotNull();
        assertThat(result.isActive()).isTrue();
        assertThat(result.getSuggestedScore()).isEqualTo(85.0);
        assertThat(result.getDangerLead()).isEqualTo("Sospecha alta de SCA");
        assertThat(result.getMeaning()).isEqualTo("Prioridad máxima en sala");
        assertThat(result.getDatasetReasons()).contains("FC elevada");
        assertThat(result.getGeminiReasons()).contains("Dolor torácico irradiado a mandíbula");
    }
}
