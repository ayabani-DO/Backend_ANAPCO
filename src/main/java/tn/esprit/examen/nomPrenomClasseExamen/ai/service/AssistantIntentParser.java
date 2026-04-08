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
import tn.esprit.examen.nomPrenomClasseExamen.ai.dto.IntentParsingResult;

import java.time.LocalDate;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantIntentParser {

    private final GroqClient groqClient;
    private final GroqProperties groqProperties;
    private final ObjectMapper objectMapper;

    public IntentParsingResult parse(String question) {
        LocalDate now = LocalDate.now();
        String systemPrompt = "Classify intent and detect response language. Return strict JSON only with schema: " +
                "{\"intent\":\"FINANCE_KPI|INCIDENT_RISK|WEATHER_RISK|ML_COST_FORECAST|UNKNOWN\",\"language\":\"FR|EN|AR\",\"confidence\":0.0,\"siteId\":null,\"year\":null,\"month\":null,\"days\":null}. " +
                "If absent use null, confidence between 0 and 1. Today year=" + now.getYear() + ", month=" + now.getMonthValue() + ".";

        try {
            String raw = groqClient.chatCompletion(groqProperties.getIntentModel(), systemPrompt, question);
            IntentParsingResult parsed = objectMapper.readValue(sanitizeJson(raw), IntentParsingResult.class);
            if (parsed.getIntent() == null) {
                parsed.setIntent(AssistantIntent.UNKNOWN);
            }
            if (parsed.getLanguage() == null) {
                parsed.setLanguage(detectLanguageFallback(question));
            }
            if (parsed.getConfidence() == null) {
                parsed.setConfidence(0.0);
            }
            return parsed;
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse intent JSON, using fallback: {}", ex.getMessage());
            return heuristicFallback(question);
        } catch (Exception ex) {
            log.warn("Intent detection failed, using fallback: {}", ex.getMessage());
            return heuristicFallback(question);
        }
    }

    private IntentParsingResult heuristicFallback(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        AssistantIntent intent = AssistantIntent.UNKNOWN;
        if (q.contains("budget") || q.contains("coût") || q.contains("cost")) {
            intent = AssistantIntent.FINANCE_KPI;
        } else if (q.contains("incident") || q.contains("risque")) {
            intent = AssistantIntent.INCIDENT_RISK;
        } else if (q.contains("weather") || q.contains("météo") || q.contains("meteo")) {
            intent = AssistantIntent.WEATHER_RISK;
        }

        return IntentParsingResult.builder()
                .intent(intent)
                .language(detectLanguageFallback(question))
                .confidence(0.4)
                .build();
    }

    private AssistantLanguage detectLanguageFallback(String question) {
        String q = question == null ? "" : question;
        if (q.matches(".*[\\u0600-\\u06FF].*")) {
            return AssistantLanguage.AR;
        }
        String lower = q.toLowerCase(Locale.ROOT);
        if (lower.contains("bonjour") || lower.contains("voir") || lower.contains("afficher")) {
            return AssistantLanguage.FR;
        }
        return AssistantLanguage.EN;
    }

    private String sanitizeJson(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }
}
