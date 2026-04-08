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
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.IntentParsingResult;
import tn.esprit.examen.nomPrenomClasseExamen.ai.ml.MlAssistantClientService;
import tn.esprit.examen.nomPrenomClasseExamen.ai.ml.MlContractDtos;
import tn.esprit.examen.nomPrenomClasseExamen.services.FinanceKpiService;
import tn.esprit.examen.nomPrenomClasseExamen.services.IncidentKpiService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final FinanceKpiService financeKpiService;
    private final IncidentKpiService incidentKpiService;
    private final WeatherRiskService weatherRiskService;

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
            case FINANCE_KPI -> financeKpiService.getKpi(siteId, year, month);
            case INCIDENT_RISK -> incidentKpiService.calculateSiteRiskScore(siteId);
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

    private Object buildMlCostDto(Long siteId, Integer year, Integer month) {
        MlContractDtos.MlPredictionRequest request = new MlContractDtos.MlPredictionRequest(
                Map.of("site_id", siteId, "year", year, "month", month),
                List.of()
        );
        MlContractDtos.MlPredictionResponse response = mlAssistantClientService.predictCost(request);
        return Map.of(
                "siteId", siteId,
                "year", year,
                "month", month,
                "mlPrediction", response
        );
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
