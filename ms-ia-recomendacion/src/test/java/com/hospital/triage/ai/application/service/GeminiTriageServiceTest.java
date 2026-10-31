package com.hospital.triage.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.triage.ai.infrastructure.persistence.SymptomRepository;
import com.hospital.triage.common.dto.AiAssistDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiTriageServiceTest {

    @Mock
    private SymptomRepository symptoms;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private GeminiClient client;
    private GeminiTriageService geminiService;

    @BeforeEach
    void setUp() {
        client = new GeminiClient(new ObjectMapper());
        geminiService = new GeminiTriageService(client, symptoms);
    }

    private ClinicalCase.ClinicalCaseBuilder baseCase() {
        return ClinicalCase.builder()
                .conditionId("sca").reason("Dolor de pecho opresivo").history("hta").age(58).sex("M")
                .heartRate(115).spo2(93).systolic(140).diastolic(90).temperature(BigDecimal.valueOf(36.8))
                .respiratoryRate(22).protocolScore(68.0).news2(3);
    }

    private void wire(String apiKey, String model) {
        ReflectionTestUtils.setField(client, "apiKey", apiKey);
        ReflectionTestUtils.setField(client, "model", model);
        ReflectionTestUtils.setField(client, "http", mockHttpClient);
    }

    @Test
    @DisplayName("TC-11: API key de Gemini ausente o vacía -> Fallback silencioso sin invocar HTTP")
    void tc11_apiKeyAusente() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        AiAssistDTO result = geminiService.assist(baseCase().build());

        assertThat(result.isActive()).isFalse();
        assertThat(result.getSuggestedScore()).isNull();
    }

    @Test
    @DisplayName("TC-12: Error HTTP, excepción o timeout de Gemini -> active=false")
    void tc12_errorHttpOTimeoutGemini() throws Exception {
        wire("test-api-key", "modelo");
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());
        when(mockHttpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Simulated timeout connection to Google Gemini API"));

        AiAssistDTO result = geminiService.assist(baseCase().build());

        assertThat(result.isActive()).isFalse();
        assertThat(result.getSuggestedScore()).isNull();
    }

    @Test
    @DisplayName("TC-13: Respuesta válida -> score, razones, recomendaciones y área sugerida")
    void tc13_respuestaValidaGemini() throws Exception {
        wire("test-api-key", "modelo");
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());
        String text = """
                {"score": 85.0, "dangerLead": "Sospecha alta de SCA", "meaning": "Prioridad máxima en sala",
                 "fromDataset": ["FC elevada"], "fromGemini": ["Dolor torácico irradiado a mandíbula"],
                 "immediateActions": ["ECG de 12 derivaciones en menos de 10 minutos"],
                 "testsToOrder": ["Troponina"], "monitoring": ["Monitor cardiaco continuo"],
                 "redFlags": ["Hipotensión"], "symptomExplanation": "El dolor que va al brazo sugiere infarto.",
                 "suggestedArea": "urgencias_criticas", "photoFindings": "no debería salir sin foto"}
                """;
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(geminiEnvelope(text));
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

        AiAssistDTO result = geminiService.assist(baseCase().build());

        assertThat(result.isActive()).isTrue();
        assertThat(result.getSuggestedScore()).isEqualTo(85.0);
        assertThat(result.getDangerLead()).isEqualTo("Sospecha alta de SCA");
        assertThat(result.getDatasetReasons()).containsExactly("FC elevada");
        assertThat(result.getGeminiReasons()).containsExactly("Dolor torácico irradiado a mandíbula");
        assertThat(result.getImmediateActions()).containsExactly("ECG de 12 derivaciones en menos de 10 minutos");
        assertThat(result.getTestsToOrder()).containsExactly("Troponina");
        assertThat(result.getMonitoring()).containsExactly("Monitor cardiaco continuo");
        assertThat(result.getRedFlags()).containsExactly("Hipotensión");
        assertThat(result.getSymptomExplanation()).contains("infarto");
        assertThat(result.getSuggestedArea()).isEqualTo("URGENCIAS_CRITICAS");
        assertThat(result.getPhotoFindings()).isNull();
    }

    @Test
    @DisplayName("TC-14: Modelo saturado (503) -> no se prueba un segundo modelo (ahorra tokens)")
    void tc14_modeloSaturadoUsaElSiguiente() throws Exception {
        wire("test-api-key", "modelo-saturado, modelo-libre");
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());

        when(mockHttpResponse.statusCode()).thenReturn(503);
        when(mockHttpResponse.body()).thenReturn("{\"error\":{\"code\":503}}");
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        doReturn(mockHttpResponse).when(mockHttpClient).send(sent.capture(), any());

        AiAssistDTO result = geminiService.assist(baseCase().build());

        assertThat(result.isActive()).isFalse();
        assertThat(sent.getAllValues()).hasSize(1);
        assertThat(sent.getAllValues().get(0).uri().toString()).contains("modelo-saturado");
    }

    @Test
    @DisplayName("TC-15: Con foto -> se envía como inlineData y vuelve el hallazgo de la foto")
    void tc15_fotoSeEnviaYVuelveHallazgo() throws Exception {
        wire("test-api-key", "modelo");
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(geminiEnvelope(
                "{\"score\": 60, \"photoFindings\": \"Quemadura de segundo grado en antebrazo\", \"suggestedArea\": \"URGENCIAS\"}"));
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        doReturn(mockHttpResponse).when(mockHttpClient).send(sent.capture(), any());

        AiAssistDTO result = geminiService.assist(baseCase()
                .photoBase64("data:image/jpeg;base64,QUJD")
                .photoMime("image/jpeg")
                .build());

        String body = bodyOf(sent.getValue());
        assertThat(body).contains("\"inlineData\"").contains("\"data\":\"QUJD\"").contains("image/jpeg");
        assertThat(result.getPhotoFindings()).isEqualTo("Quemadura de segundo grado en antebrazo");
        assertThat(result.getSuggestedArea()).isEqualTo("URGENCIAS");
    }

    @Test
    @DisplayName("TC-16: El mismo caso dos veces (calcular y confirmar) llama a Gemini una sola vez")
    void tc16_casoRepetidoSaleDeMemoria() throws Exception {
        wire("test-api-key", "modelo");
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(geminiEnvelope("{\"score\": 70.0}"));
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

        AiAssistDTO first = geminiService.assist(baseCase().localModelScore(55.0).build());
        AiAssistDTO second = geminiService.assist(baseCase().localModelScore(null).build());

        assertThat(first.getSuggestedScore()).isEqualTo(70.0);
        assertThat(second.getSuggestedScore()).isEqualTo(70.0);
        org.mockito.Mockito.verify(mockHttpClient, org.mockito.Mockito.times(1)).send(any(), any());
        assertThat(client.usedToday()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-17: Pasado el tope por minuto no se llama a Gemini y el triaje sigue con el protocolo")
    void tc17_topePorMinuto() throws Exception {
        wire("test-api-key", "modelo");
        ReflectionTestUtils.setField(client, "perMinuteLimit", 2);
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(geminiEnvelope("{\"score\": 70.0}"));
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

        assertThat(geminiService.assist(baseCase().age(30).build()).isActive()).isTrue();
        assertThat(geminiService.assist(baseCase().age(31).build()).isActive()).isTrue();
        AiAssistDTO third = geminiService.assist(baseCase().age(32).build());

        assertThat(third.isActive()).isFalse();
        assertThat(third.getProtocolScore()).isEqualTo(68.0);
        org.mockito.Mockito.verify(mockHttpClient, org.mockito.Mockito.times(2)).send(any(), any());
    }

    @Test
    @DisplayName("TC-18: Aunque la lista tenga más modelos, solo se prueba uno por pregunta")
    void tc18_maximoDosModelos() throws Exception {
        wire("test-api-key", "uno,dos,tres,cuatro");
        when(symptoms.findByActiveTrueOrderBySeverityDesc()).thenReturn(List.of());
        when(mockHttpResponse.statusCode()).thenReturn(503);
        when(mockHttpResponse.body()).thenReturn("{\"error\":{\"code\":503}}");
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

        assertThat(geminiService.assist(baseCase().build()).isActive()).isFalse();
        org.mockito.Mockito.verify(mockHttpClient, org.mockito.Mockito.times(1)).send(any(), any());
    }

    static String geminiEnvelope(String innerJson) throws Exception {
        String escaped = new ObjectMapper().writeValueAsString(innerJson);
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + escaped + "}]}}]}";
    }

    static String bodyOf(HttpRequest request) throws Exception {
        var publisher = request.bodyPublisher().orElseThrow();
        var future = new CompletableFuture<String>();
        var buffer = new StringBuilder();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                buffer.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(buffer.toString());
            }
        });
        return future.get(5, TimeUnit.SECONDS);
    }
}
