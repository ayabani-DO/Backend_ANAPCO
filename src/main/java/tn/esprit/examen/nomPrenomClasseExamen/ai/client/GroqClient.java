package tn.esprit.examen.nomPrenomClasseExamen.ai.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tn.esprit.examen.nomPrenomClasseExamen.ai.config.GroqProperties;

import java.util.List;

@Component
@RequiredArgsConstructor
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

        ResponseEntity<GroqChatResponse> response = restTemplate.exchange(
                groqProperties.getBaseUrl() + "/chat/completions",
                HttpMethod.POST,
                request,
                GroqChatResponse.class
        );

        GroqChatResponse body = response.getBody();
        if (body == null || body.choices() == null || body.choices().isEmpty() || body.choices().get(0).message() == null) {
            throw new IllegalStateException("Invalid response from Groq API");
        }
        return body.choices().get(0).message().content();
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
