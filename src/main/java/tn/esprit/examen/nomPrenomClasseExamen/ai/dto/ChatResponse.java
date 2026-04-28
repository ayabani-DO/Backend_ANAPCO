package tn.esprit.examen.nomPrenomClasseExamen.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ChatResponse {

    private String answer;

    private IntentDetectionResult detectedIntent;

    private AiToolResult data;

    private List<String> suggestions;

    @Getter
    @Builder
    public static class IntentDetectionResult {
        private String intent;
        private Double confidence;
        private Map<String, String> entities;
    }

    @Getter
    @Builder
    public static class AiToolResult {
        private String toolName;
        private Object payload;
        private String summary;
    }
}
