package tn.esprit.examen.nomPrenomClasseExamen.market.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MlPredictionService;

import java.util.Map;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
@Tag(name = "ML Predictions", description = "Train models and get cost/risk predictions via XGBoost")
public class MlPredictionController {

    private final MlPredictionService mlPredictionService;

    @PostMapping("/train")
    @Operation(summary = "Train both ML models (cost forecast + risk classification) from stored feature snapshots")
    public Map<String, Object> trainModels() {
        return mlPredictionService.trainModels();
    }

    @GetMapping("/predict/cost/{siteId}/{year}/{month}")
    @Operation(summary = "Predict next month total cost for a site (Model 1 — XGBoost Regressor)")
    public MlPredictionService.CostPredictionResult predictCost(
            @PathVariable Long siteId,
            @PathVariable int year,
            @PathVariable int month) {
        return mlPredictionService.predictCost(siteId, year, month);
    }

    @GetMapping("/predict/risk/{siteId}/{year}/{month}")
    @Operation(summary = "Predict risk class for a site (Model 2 — XGBoost Classifier)")
    public MlPredictionService.RiskPredictionResult predictRisk(
            @PathVariable Long siteId,
            @PathVariable int year,
            @PathVariable int month) {
        return mlPredictionService.predictRisk(siteId, year, month);
    }

    @GetMapping("/explain/cost/{siteId}/{year}/{month}")
    @Operation(summary = "SHAP explanation for cost prediction — shows which features drive the prediction")
    public Map<String, Object> explainCost(
            @PathVariable Long siteId,
            @PathVariable int year,
            @PathVariable int month) {
        return mlPredictionService.explainCost(siteId, year, month);
    }

    @GetMapping("/explain/risk/{siteId}/{year}/{month}")
    @Operation(summary = "SHAP explanation for risk prediction — shows which features drive the risk class")
    public Map<String, Object> explainRisk(
            @PathVariable Long siteId,
            @PathVariable int year,
            @PathVariable int month) {
        return mlPredictionService.explainRisk(siteId, year, month);
    }

    @GetMapping("/health")
    @Operation(summary = "Check ML microservice health and model availability")
    public Map<String, Object> health() {
        return mlPredictionService.healthCheck();
    }
}
