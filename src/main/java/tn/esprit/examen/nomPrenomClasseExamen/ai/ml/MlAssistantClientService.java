package tn.esprit.examen.nomPrenomClasseExamen.ai.ml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprit.examen.nomPrenomClasseExamen.ai.exception.AssistantDependencyException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MlAssistantClientService {

    private final RestTemplate restTemplate;

    @Value("${ml.service.base-url:http://localhost:5001}")
    private String mlServiceBaseUrl;

    public MlContractDtos.MlPredictionResponse predictCost(MlContractDtos.MlPredictionRequest request) {
        try {
            ResponseEntity<MlContractDtos.MlPredictionResponse> response = restTemplate.postForEntity(
                    mlServiceBaseUrl + "/predict/cost",
                    request,
                    MlContractDtos.MlPredictionResponse.class
            );
            return response.getBody();
        } catch (Exception ex) {
            log.error("ML cost prediction call failed: {}", ex.getMessage(), ex);
            throw new AssistantDependencyException("ML service unavailable for cost prediction", ex);
        }
    }

    public boolean isReachable() {
        try {
            ResponseEntity<MlContractDtos.MlHealthResponse> response = restTemplate.exchange(
                    mlServiceBaseUrl + "/health",
                    HttpMethod.GET,
                    null,
                    MlContractDtos.MlHealthResponse.class
            );
            MlContractDtos.MlHealthResponse body = response.getBody();
            return body != null && body.status() != null && "DOWN".compareToIgnoreCase(body.status()) != 0;
        } catch (Exception ex) {
            log.warn("ML health check failed: {}", ex.getMessage());
            return false;
        }
    }

    public Map<String, Object> healthSummary() {
        boolean reachable = isReachable();
        return Map.of("reachable", reachable, "baseUrl", mlServiceBaseUrl);
    }
}
