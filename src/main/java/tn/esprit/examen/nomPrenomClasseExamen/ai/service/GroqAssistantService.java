package tn.esprit.examen.nomPrenomClasseExamen.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.ai.client.GroqClient;
import tn.esprit.examen.nomPrenomClasseExamen.ai.config.GroqProperties;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantResponseDto;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.DetectedIntent;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.IntentDetectionResult;
import tn.esprit.examen.nomPrenomClasseExamen.services.FinanceKpiService;
import tn.esprit.examen.nomPrenomClasseExamen.services.IncidentKpiService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class GroqAssistantService {

    private final GroqClient groqClient;
    private final GroqProperties groqProperties;
    private final ObjectMapper objectMapper;
    private final FinanceKpiService financeKpiService;
    private final IncidentKpiService incidentKpiService;
    private final WeatherRiskService weatherRiskService;

    public AssistantResponseDto ask(String question) {
        IntentDetectionResult intentResult = detectIntent(question);
        Object dtoPayload = mapIntentToDto(intentResult);
        String naturalAnswer = buildNaturalAnswer(question, intentResult.getIntent(), dtoPayload);

        return AssistantResponseDto.builder()
                .intent(intentResult.getIntent())
                .data(dtoPayload)
                .naturalLanguageAnswer(naturalAnswer)
                .build();
    }

    private IntentDetectionResult detectIntent(String question) {
        LocalDate now = LocalDate.now();

        String systemPrompt = "You are an intent classifier for backend analytics. " +
                "Return STRICT JSON with this schema: " +
                "{\"intent\":\"FINANCE_KPI|INCIDENT_RISK|WEATHER_RISK|UNKNOWN\",\"siteId\":number|null,\"year\":number|null,\"month\":number|null,\"days\":number|null}. " +
                "Do not include markdown. If values are missing, keep null. " +
                "Today's year=" + now.getYear() + " and month=" + now.getMonthValue() + ".";

        String raw = groqClient.chatCompletion(groqProperties.getIntentModel(), systemPrompt, question);

        try {
            return objectMapper.readValue(sanitizeJson(raw), IntentDetectionResult.class);
        } catch (JsonProcessingException ex) {
            return IntentDetectionResult.builder().intent(DetectedIntent.UNKNOWN).build();
        }
    }

    private Object mapIntentToDto(IntentDetectionResult intentResult) {
        Long siteId = intentResult.getSiteId() != null ? intentResult.getSiteId() : 1L;
        LocalDate now = LocalDate.now();
        Integer year = intentResult.getYear() != null ? intentResult.getYear() : now.getYear();
        Integer month = intentResult.getMonth() != null ? intentResult.getMonth() : now.getMonthValue();
        Integer days = intentResult.getDays() != null ? intentResult.getDays() : 30;

        return switch (intentResult.getIntent()) {
            case FINANCE_KPI -> financeKpiService.getKpi(siteId, year, month);
            case INCIDENT_RISK -> incidentKpiService.calculateSiteRiskScore(siteId);
            case WEATHER_RISK -> weatherRiskService.getLatestAssessment(siteId);
            case UNKNOWN -> new UnsupportedIntentDto("Intent not recognized. Try finance KPI, incident risk, or weather risk.", siteId, year, month, days);
        };
    }

    private String buildNaturalAnswer(String question, DetectedIntent intent, Object dtoPayload) {
        String dtoAsJson;
        try {
            dtoAsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dtoPayload);
        } catch (JsonProcessingException ex) {
            dtoAsJson = dtoPayload.toString();
        }

        String systemPrompt = "You are a helpful assistant for an enterprise dashboard. " +
                "Use the DTO data to write a concise, clear, business-friendly response. " +
                "Do not invent data. If intent is UNKNOWN, ask user to rephrase with needed parameters.";

        String userPrompt = "Intent=" + intent + "\nUser question=" + question + "\nDTO data:\n" + dtoAsJson;

        return groqClient.chatCompletion(groqProperties.getAnswerModel(), systemPrompt, userPrompt);
    }

    private String sanitizeJson(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("```")) {
            int firstBrace = trimmed.indexOf('{');
            int lastBrace = trimmed.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        return trimmed;
    }

    private record UnsupportedIntentDto(String message, Long siteId, Integer year, Integer month, Integer days) {
    }
}
