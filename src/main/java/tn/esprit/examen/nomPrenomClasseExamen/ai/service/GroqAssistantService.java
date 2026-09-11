package tn.esprit.examen.nomPrenomClasseExamen.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.ai.client.GroqClient;
import tn.esprit.examen.nomPrenomClasseExamen.ai.config.GroqProperties;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantIntent;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantLanguage;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.AssistantResponseDto;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.ChatRequest;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.ChatResponse;
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.IntentParsingResult;
import tn.esprit.examen.nomPrenomClasseExamen.ai.exception.AssistantDependencyException;
import tn.esprit.examen.nomPrenomClasseExamen.ai.ml.MlAssistantClientService;
import tn.esprit.examen.nomPrenomClasseExamen.ai.ml.MlContractDtos;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MlPredictionService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroqAssistantService {

    private final GroqClient groqClient;
    private final GroqProperties groqProperties;
    private final ObjectMapper objectMapper;
    private final AssistantIntentParser intentParser;
    private final MlAssistantClientService mlAssistantClientService;
    private final FinancialAnalyticsService financialAnalyticsService;
    private final OperationalAnalyticsService operationalAnalyticsService;
    private final WeatherRiskService weatherRiskService;
    private final MlPredictionService mlPredictionService;

    public ChatResponse chat(ChatRequest request) {
        IntentParsingResult parsed = intentParser.parse(request.getMessage());

        if (request.getLanguage() != null) {
            switch (request.getLanguage().toLowerCase(Locale.ROOT)) {
                case "fr" -> parsed.setLanguage(AssistantLanguage.FR);
                case "en" -> parsed.setLanguage(AssistantLanguage.EN);
                case "ar" -> parsed.setLanguage(AssistantLanguage.AR);
            }
        }

        Object dtoPayload = mapIntentToDto(parsed);
        List<String> suggestions = buildSuggestions(parsed.getIntent(), parsed.getLanguage());
        String naturalAnswer = buildNaturalAnswer(request.getMessage(), parsed, dtoPayload, suggestions);

        String toolName = switch (parsed.getIntent()) {
            case FINANCE_KPI -> "getFinancialAnalytics";
            case INCIDENT_RISK -> "getOperationalAnalytics";
            case WEATHER_RISK -> "getWeatherRisk";
            case ML_COST_FORECAST -> "predictCost";
            case UNKNOWN -> "none";
        };

        Map<String, String> entities = new HashMap<>();
        if (parsed.getSiteId() != null) entities.put("siteId", String.valueOf(parsed.getSiteId()));
        if (parsed.getYear() != null) entities.put("year", String.valueOf(parsed.getYear()));
        if (parsed.getMonth() != null) entities.put("month", String.valueOf(parsed.getMonth()));

        return ChatResponse.builder()
                .answer(naturalAnswer)
                .detectedIntent(ChatResponse.IntentDetectionResult.builder()
                        .intent(parsed.getIntent().name())
                        .confidence(parsed.getConfidence())
                        .entities(entities)
                        .build())
                .data(ChatResponse.AiToolResult.builder()
                        .toolName(toolName)
                        .payload(dtoPayload)
                        .summary(toolName.equals("none") ? "No matching service" : "Data retrieved successfully")
                        .build())
                .suggestions(suggestions)
                .build();
    }

    public AssistantResponseDto ask(String question) {
        IntentParsingResult parsed = intentParser.parse(question);
        Object dtoPayload = mapIntentToDto(parsed);
        List<String> suggestions = buildSuggestions(parsed.getIntent(), parsed.getLanguage());
        String naturalAnswer = buildNaturalAnswer(question, parsed, dtoPayload, suggestions);

        return AssistantResponseDto.builder()
                .intent(parsed.getIntent())
                .language(parsed.getLanguage())
                .confidence(parsed.getConfidence())
                .suggestions(suggestions)
                .data(dtoPayload)
                .naturalLanguageAnswer(naturalAnswer)
                .build();
    }

    public Map<String, Object> assistantStatus() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("groq", groqClient.healthSummary());
        summary.put("ml", mlAssistantClientService.healthSummary());
        summary.put("status", "OK");
        return summary;
    }

    private Object mapIntentToDto(IntentParsingResult parsed) {
        Long siteId = parsed.getSiteId() != null ? parsed.getSiteId() : 1L;
        LocalDate now = LocalDate.now();
        Integer year = parsed.getYear() != null ? parsed.getYear() : now.getYear();
        Integer month = parsed.getMonth() != null ? parsed.getMonth() : now.getMonthValue();

        return switch (parsed.getIntent()) {
            // Route through the Analytics layer so the assistant answers with the SAME numbers as the dashboard.
            case FINANCE_KPI -> financialAnalyticsService.getFinancialKpi(siteId, year, month);
            case INCIDENT_RISK -> operationalAnalyticsService.getOperationalKpi(siteId, year, month);
            case WEATHER_RISK -> weatherRiskService.getLatestAssessment(siteId);
            case ML_COST_FORECAST -> buildMlCostDto(siteId, year, month);
            case UNKNOWN -> Map.of(
                    "message", "Intent not recognized. Please ask about finance KPI, incidents, weather risk, or forecasted cost.",
                    "siteId", siteId,
                    "year", year,
                    "month", month
            );
        };
    }

    /**
     * Routes the chatbot's ML_COST_FORECAST intent through the same canonical
     * {@link MlPredictionService#predictCost} path the Dashboard AI view uses — full
     * {@code MonthlyFeatureSnapshot} feature payload plus up to 6 months of history — instead of
     * independently posting a thin {@code site_id/year/month} payload with an empty history to
     * Flask (see FIX 4). The response is re-wrapped into the pre-existing
     * {@code MlPredictionResponse} shape so the chatbot's JSON contract is unchanged.
     */
    private Object buildMlCostDto(Long siteId, Integer year, Integer month) {
        try {
            MlPredictionService.CostPredictionResult result = mlPredictionService.predictCost(siteId, year, month);
            Map<String, Object> predictionEntry = new LinkedHashMap<>();
            predictionEntry.put("predicted_next_month_cost_eur", result.getPredictedNextMonthCostEur());
            MlContractDtos.MlPredictionResponse response = new MlContractDtos.MlPredictionResponse(
                    "OK",
                    List.of(predictionEntry),
                    null
            );
            return Map.of(
                    "siteId", siteId,
                    "year", year,
                    "month", month,
                    "mlPrediction", response
            );
        } catch (Exception ex) {
            throw new AssistantDependencyException("ML service unavailable for cost prediction", ex);
        }
    }

    private String buildNaturalAnswer(String question, IntentParsingResult parsed, Object dtoPayload, List<String> suggestions) {
        String dtoAsJson;
        try {
            dtoAsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dtoPayload);
        } catch (JsonProcessingException ex) {
            dtoAsJson = String.valueOf(dtoPayload);
        }

        String languageInstruction = switch (parsed.getLanguage()) {
            case FR -> "Respond in French.";
            case EN -> "Respond in English.";
            case AR -> "Respond in Arabic.";
        };

        String systemPrompt = "You are an enterprise analytics assistant. " +
                languageInstruction +
                " Use provided DTO only. Keep response concise and professional. Mention uncertainty if confidence is low.";

        String userPrompt = "Intent=" + parsed.getIntent() +
                "\nConfidence=" + parsed.getConfidence() +
                "\nUser question=" + question +
                "\nDTO data=" + dtoAsJson +
                "\nSuggestions=" + suggestions;

        return groqClient.chatCompletion(groqProperties.getAnswerModel(), systemPrompt, userPrompt);
    }

    private List<String> buildSuggestions(AssistantIntent intent, AssistantLanguage language) {
        return switch (intent) {
            case INCIDENT_RISK -> switch (language) {
                case FR -> List.of("Voir les incidents critiques", "Afficher les incidents ouverts");
                case EN -> List.of("Show critical incidents", "Show open incidents");
                case AR -> List.of("عرض الحوادث الحرجة", "عرض الحوادث المفتوحة");
            };
            case FINANCE_KPI -> switch (language) {
                case FR -> List.of("Afficher le budget maintenance", "Comparer budget et réel");
                case EN -> List.of("Show maintenance budget", "Compare budget vs actual");
                case AR -> List.of("عرض ميزانية الصيانة", "مقارنة الميزانية بالمصاريف الفعلية");
            };
            case WEATHER_RISK -> switch (language) {
                case FR -> List.of("Voir le dernier risque météo", "Afficher les alertes météo");
                case EN -> List.of("Show latest weather risk", "Show weather alerts");
                case AR -> List.of("عرض آخر مخاطر الطقس", "عرض تنبيهات الطقس");
            };
            case ML_COST_FORECAST -> switch (language) {
                case FR -> List.of("Voir la prévision de coût", "Comparer prévision et budget");
                case EN -> List.of("Show cost forecast", "Compare forecast with budget");
                case AR -> List.of("عرض توقع التكاليف", "مقارنة التوقع مع الميزانية");
            };
            case UNKNOWN -> switch (language) {
                case FR -> List.of("Voir les incidents critiques", "Afficher le budget maintenance");
                case EN -> List.of("Show critical incidents", "Show maintenance budget");
                case AR -> List.of("عرض الحوادث الحرجة", "عرض ميزانية الصيانة");
            };
        };
    }
}
