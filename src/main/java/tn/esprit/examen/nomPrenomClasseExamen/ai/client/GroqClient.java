package tn.esprit.examen.nomPrenomClasseExamen.ai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tn.esprit.examen.nomPrenomClasseExamen.ai.config.GroqProperties;
import tn.esprit.examen.nomPrenomClasseExamen.ai.exception.AssistantDependencyException;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GroqClient {

    private final RestTemplate restTemplate;
    private final GroqProperties groqProperties;

    public String chatCompletion(String model, String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqProperties.getApiKey());

        GroqChatRequest payload = new GroqChatRequest(
                model,
                List.of(
                        new GroqMessage("system", systemPrompt),
                        new GroqMessage("user", userPrompt)
                ),
                0.2
        );

        HttpEntity<GroqChatRequest> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<GroqChatResponse> response = restTemplate.exchange(
                    groqProperties.getBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    request,
                    GroqChatResponse.class
            );

            GroqChatResponse body = response.getBody();
            if (body == null || body.choices() == null || body.choices().isEmpty() || body.choices().get(0).message() == null) {
                throw new AssistantDependencyException("Invalid response from Groq API", null);
            }
            return body.choices().get(0).message().content();
        } catch (RestClientException ex) {
            log.error("Groq API call failed: {}", ex.getMessage(), ex);
            throw new AssistantDependencyException("Groq API unreachable or timed out", ex);
        }
    }

    public boolean isReachable() {
        try {
            String response = chatCompletion(groqProperties.getIntentModel(), "Reply with OK", "health check");
            return response != null && !response.isBlank();
        } catch (Exception ex) {
            log.warn("Groq health check failed: {}", ex.getMessage());
            return false;
        }
    }

    public Map<String, Object> healthSummary() {
        return Map.of("reachable", isReachable(), "baseUrl", groqProperties.getBaseUrl());
    }

    private record GroqChatRequest(String model, List<GroqMessage> messages, Double temperature) {
    }

    private record GroqMessage(String role, String content) {
    }

    private record GroqChatResponse(List<GroqChoice> choices) {
    }

    private record GroqChoice(GroqMessage message) {
    }
}
