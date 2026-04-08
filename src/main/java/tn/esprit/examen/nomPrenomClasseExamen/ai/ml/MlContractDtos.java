package tn.esprit.examen.nomPrenomClasseExamen.ai.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

public class MlContractDtos {

    public record MlPredictionRequest(Map<String, Object> features, List<Map<String, Object>> history) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MlPredictionResponse(String status, List<Map<String, Object>> predictions, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MlHealthResponse(String status, String error) {
    }
}
