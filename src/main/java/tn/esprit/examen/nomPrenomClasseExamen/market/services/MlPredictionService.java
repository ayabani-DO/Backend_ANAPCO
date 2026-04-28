package tn.esprit.examen.nomPrenomClasseExamen.market.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.entities.MonthlyFeatureSnapshot;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MonthlyFeatureSnapshotRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Integrates with the Python ML microservice for:
 * - Training models from snapshot data
 * - Predicting next month cost (Model 1)
 * - Predicting risk class (Model 2)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MlPredictionService {

    private final RestTemplate restTemplate;
    private final MonthlyFeatureSnapshotRepository snapshotRepository;
    private final MarketDataService marketDataService;
    private final MonthlyFeatureAggregationService aggregationService;
    private final SitesRepository sitesRepository;

    @Value("${ml.service.base-url:http://localhost:5001}")
    private String mlServiceBaseUrl;

    /** Max age of a trained model before automatic re-training (in minutes). Default = 60 min. */
    @Value("${ml.auto-retrain-interval-minutes:60}")
    private long autoRetrainIntervalMinutes;

    /** Track when the model was last trained to avoid retraining too often. */
    private volatile LocalDateTime lastTrainedAt = null;

    // ── Training ───────────────────────────────────────────

    public Map<String, Object> trainModels() {
        List<Map<String, Object>> costData = snapshotRepository.findAllWithCostTarget().stream()
                .map(this::toFeatureMap).toList();
        List<Map<String, Object>> riskData = snapshotRepository.findAllWithRiskTarget().stream()
                .map(this::toFeatureMap).toList();

        Map<String, Object> body = Map.of("cost_data", costData, "risk_data", riskData);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(mlServiceBaseUrl + "/train", body, Map.class);
            log.info("ML training response: {}", response);
            return response != null ? response : Map.of("status", "ERROR", "error", "Empty response from ML service");
        } catch (Exception e) {
            log.error("Failed to call ML training endpoint: {}", e.getMessage(), e);
            return Map.of("status", "ERROR", "error", e.getMessage());
        }
    }

    // ── Auto-Update Pipeline ────────────────────────────────

    /**
     * Called before every prediction. Syncs latest prices, enriches snapshots,
     * and re-trains the models if the model is stale.
     */
    private synchronized void ensureModelFresh() {
        boolean needsRetrain = lastTrainedAt == null
                || lastTrainedAt.plusMinutes(autoRetrainIntervalMinutes).isBefore(LocalDateTime.now());

        if (!needsRetrain) {
            log.debug("Model still fresh (trained at {}). Skipping auto-update.", lastTrainedAt);
            return;
        }

        log.info("=== Auto-update pipeline started ===");
        try {
            // 1. Sync latest market prices
            LocalDate today = LocalDate.now();
            LocalDate oneYearAgo = today.minusYears(1);

            marketDataService.syncLatestOilPrice();
            log.info("[Auto-update] Oil price sync done");

            // Sync energy for all distinct country codes in sites
            Set<String> countryCodes = sitesRepository.findAll().stream()
                    .map(Sites::getCountryCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (countryCodes.isEmpty()) countryCodes.add("DE");
            for (String cc : countryCodes) {
                marketDataService.syncLatestEnergyPrice(cc);
            }
            log.info("[Auto-update] Energy price sync done for {}", countryCodes);

            // 2. Enrich existing snapshots with new market prices
            int enriched = aggregationService.enrichMarketPrices();
            log.info("[Auto-update] Enriched {} snapshots with market prices", enriched);

            // 3. Re-train models
            Map<String, Object> trainResult = trainModels();
            log.info("[Auto-update] Training result: {}", trainResult);

            lastTrainedAt = LocalDateTime.now();
            log.info("=== Auto-update pipeline completed at {} ===", lastTrainedAt);

        } catch (Exception e) {
            log.error("Auto-update pipeline failed: {}. Predictions will use existing model.", e.getMessage(), e);
        }
    }

    // ── History helper ────────────────────────────────────

    /**
     * Fetch the last N snapshots for a site in chronological order, to provide
     * as history to the ML service for lag/rolling feature computation.
     */
    private List<Map<String, Object>> getHistory(Long siteId, int year, int month) {
        List<MonthlyFeatureSnapshot> all = snapshotRepository.findBySiteIdSiteOrderByYearAscMonthAsc(siteId);
        // Keep only snapshots strictly before the requested month
        List<Map<String, Object>> history = new ArrayList<>();
        for (MonthlyFeatureSnapshot s : all) {
            if (s.getYear() < year || (s.getYear() == year && s.getMonth() < month)) {
                history.add(toFeatureMap(s));
            }
        }
        // Keep only last 6 months of history (enough for rolling_6m)
        if (history.size() > 6) {
            history = history.subList(history.size() - 6, history.size());
        }
        return history;
    }

    // ── Cost Forecast ──────────────────────────────────────

    public CostPredictionResult predictCost(Long siteId, int year, int month) {
        ensureModelFresh();
        MonthlyFeatureSnapshot snapshot = snapshotRepository
                .findBySiteIdSiteAndYearAndMonth(siteId, year, month)
                .orElseGet(() -> {
                    log.info("No snapshot for site {} {}-{}, auto-computing...", siteId, year, month);
                    Sites site = sitesRepository.findById(siteId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found: " + siteId));
                    return aggregationService.computeForSite(site, year, month);
                });

        Map<String, Object> features = toFeatureMap(snapshot);
        List<Map<String, Object>> history = getHistory(siteId, year, month);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("features", features);
        body.put("history", history);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(mlServiceBaseUrl + "/predict/cost", body, Map.class);
            if (response != null && "OK".equals(response.get("status"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> predictions = (List<Map<String, Object>>) response.get("predictions");
                if (predictions == null && response.get("data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    predictions = (List<Map<String, Object>>) data.get("predictions");
                }
                if (predictions != null && !predictions.isEmpty()) {
                    Map<String, Object> pred = predictions.get(0);
                    return CostPredictionResult.builder()
                            .siteId(siteId)
                            .year(year)
                            .month(month)
                            .predictedNextMonthCostEur(toDouble(pred.get("predicted_next_month_cost_eur")))
                            .build();
                }
            }
            throw new RuntimeException("Unexpected ML response: " + response);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call ML cost prediction: " + e.getMessage(), e);
        }
    }

    // ── Risk Classification ────────────────────────────────

    public RiskPredictionResult predictRisk(Long siteId, int year, int month) {
        ensureModelFresh();

        MonthlyFeatureSnapshot snapshot = snapshotRepository
                .findBySiteIdSiteAndYearAndMonth(siteId, year, month)
                .orElseGet(() -> {
                    log.info("No snapshot for site {} {}-{}, auto-computing...", siteId, year, month);
                    Sites site = sitesRepository.findById(siteId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found: " + siteId));
                    return aggregationService.computeForSite(site, year, month);
                });

        Map<String, Object> features = toFeatureMap(snapshot);
        List<Map<String, Object>> history = getHistory(siteId, year, month);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("features", features);
        body.put("history", history);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(mlServiceBaseUrl + "/predict/risk", body, Map.class);
            if (response != null && "OK".equals(response.get("status"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> predictions = (List<Map<String, Object>>) response.get("predictions");
                if (predictions == null && response.get("data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    predictions = (List<Map<String, Object>>) data.get("predictions");
                }
                if (predictions != null && !predictions.isEmpty()) {
                    Map<String, Object> pred = predictions.get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Double> probabilities = (Map<String, Double>) pred.get("probabilities");
                    return RiskPredictionResult.builder()
                            .siteId(siteId)
                            .year(year)
                            .month(month)
                            .riskClass((String) pred.get("risk_class"))
                            .probabilities(probabilities)
                            .build();
                }
            }
            throw new RuntimeException("Unexpected ML response: " + response);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call ML risk prediction: " + e.getMessage(), e);
        }
    }

    // ── SHAP Explanations ───────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> explainCost(Long siteId, int year, int month) {
        MonthlyFeatureSnapshot snapshot = snapshotRepository
                .findBySiteIdSiteAndYearAndMonth(siteId, year, month)
                .orElseGet(() -> {
                    log.info("No snapshot for site {} {}-{}, auto-computing...", siteId, year, month);
                    Sites site = sitesRepository.findById(siteId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found: " + siteId));
                    return aggregationService.computeForSite(site, year, month);
                });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("features", toFeatureMap(snapshot));
        body.put("history", getHistory(siteId, year, month));

        try {
            Map<String, Object> response = restTemplate.postForObject(mlServiceBaseUrl + "/explain/cost", body, Map.class);
            return response != null ? response : Map.of("status", "ERROR", "error", "Empty response");
        } catch (Exception e) {
            return Map.of("status", "ERROR", "error", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> explainRisk(Long siteId, int year, int month) {
        MonthlyFeatureSnapshot snapshot = snapshotRepository
                .findBySiteIdSiteAndYearAndMonth(siteId, year, month)
                .orElseGet(() -> {
                    log.info("No snapshot for site {} {}-{}, auto-computing...", siteId, year, month);
                    Sites site = sitesRepository.findById(siteId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found: " + siteId));
                    return aggregationService.computeForSite(site, year, month);
                });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("features", toFeatureMap(snapshot));
        body.put("history", getHistory(siteId, year, month));

        try {
            Map<String, Object> response = restTemplate.postForObject(mlServiceBaseUrl + "/explain/risk", body, Map.class);
            return response != null ? response : Map.of("status", "ERROR", "error", "Empty response");
        } catch (Exception e) {
            return Map.of("status", "ERROR", "error", e.getMessage());
        }
    }

    // ── Health check ───────────────────────────────────────

    public Map<String, Object> healthCheck() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(mlServiceBaseUrl + "/health", Map.class);
            return response != null ? response : Map.of("status", "DOWN");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    // ── Feature map builder ────────────────────────────────

    private Map<String, Object> toFeatureMap(MonthlyFeatureSnapshot s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("site_id", s.getSite().getIdSite());
        row.put("year", s.getYear());
        row.put("month", s.getMonth());
        row.put("total_cost_eur", s.getTotalCostEur());
        row.put("previous_month_total_cost_eur", s.getPreviousMonthTotalCostEur());
        row.put("incident_cost_eur", s.getIncidentCostEur());
        row.put("maintenance_cost_eur", s.getMaintenanceCostEur());
        row.put("manual_expense_eur", s.getManualExpenseEur());
        row.put("budget_eur", s.getBudgetEur());
        row.put("budget_variance_pct", s.getBudgetVariancePct());
        row.put("incident_count", s.getIncidentCount());
        row.put("critical_incident_count", s.getCriticalIncidentCount());
        row.put("high_incident_count", s.getHighIncidentCount());
        row.put("avg_incident_severity", s.getAvgIncidentSeverity());
        row.put("preventive_maintenance_count", s.getPreventiveMaintenanceCount());
        row.put("corrective_maintenance_count", s.getCorrectiveMaintenanceCount());
        row.put("inspection_count", s.getInspectionCount());
        row.put("corrective_preventive_ratio", s.getCorrectivePreventiveRatio());
        row.put("avg_mtbf", s.getAvgMtbf());
        row.put("avg_mttr", s.getAvgMttr());
        row.put("oil_price_avg_usd", s.getOilPriceAvgUsd());
        row.put("gas_price_avg_eur_mwh", s.getGasPriceAvgEurMwh());
        row.put("electricity_price_avg_eur_mwh", s.getElectricityPriceAvgEurMwh());
        row.put("weather_risk_score_avg", s.getWeatherRiskScoreAvg());
        row.put("weather_alert_count", s.getWeatherAlertCount());
        row.put("site_type", s.getSiteType());
        row.put("equipment_count", s.getEquipmentCount());
        row.put("dominant_equipment_category", s.getDominantEquipmentCategory());
        row.put("season", s.getSeason());
        row.put("next_month_total_cost_eur", s.getNextMonthTotalCostEur());
        row.put("risk_class", s.getRiskClass());
        return row;
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return Double.parseDouble(obj.toString());
    }

    // ── Result DTOs ────────────────────────────────────────

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CostPredictionResult {
        private Long siteId;
        private Integer year;
        private Integer month;
        private Double predictedNextMonthCostEur;
    }

    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RiskPredictionResult {
        private Long siteId;
        private Integer year;
        private Integer month;
        private String riskClass;
        private Map<String, Double> probabilities;
    }
}
