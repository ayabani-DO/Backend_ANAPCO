package tn.esprit.examen.nomPrenomClasseExamen.decision.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.GlobalRiskDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.GlobalRiskDTO.MlInsight;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.GlobalRiskDTO.RiskComponent;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MlPredictionService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherRiskAssessmentDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.util.ArrayList;
import java.util.List;

/**
 * Decision Layer — combines the analytics KPIs, the weather assessment and (optionally) the ML risk
 * model into one global risk picture. It only <b>scores</b> pre-computed inputs; no KPI is recomputed
 * here. Weather and ML are consumed defensively so the endpoint never fails when they are unavailable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngine {

    private final OperationalAnalyticsService operationalAnalytics;
    private final FinancialAnalyticsService financialAnalytics;
    private final WeatherRiskService weatherRiskService;
    private final MlPredictionService mlPredictionService;

    public GlobalRiskDTO assessGlobalRisk(Long siteId, int year, int month) {
        return assessGlobalRisk(siteId, year, month,
                operationalAnalytics.getOperationalKpi(siteId, year, month),
                financialAnalytics.getFinancialKpi(siteId, year, month));
    }

    /**
     * Overload for orchestrators (e.g. the Dashboard layer) that already hold the analytics KPIs,
     * so they are not recomputed. Weather and ML are still consumed here.
     */
    public GlobalRiskDTO assessGlobalRisk(Long siteId, int year, int month,
                                          OperationalKpiDTO operational, FinancialKpiDTO financial) {
        RiskComponent operationalRisk = scoreOperational(operational);
        RiskComponent financialRisk = scoreFinancial(financial);
        RiskComponent weatherRisk = scoreWeather(siteId);

        int overallScore = (int) Math.round(
                operationalRisk.score() * 0.40 + financialRisk.score() * 0.35 + weatherRisk.score() * 0.25);
        RiskComponent overallRisk = new RiskComponent(levelFromScore(overallScore), overallScore, List.of());

        MlInsight mlInsight = tryMlRisk(siteId, year, month);
        List<String> recommendations = buildRecommendations(operationalRisk, financialRisk, weatherRisk, mlInsight);

        return GlobalRiskDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .operationalRisk(operationalRisk)
                .financialRisk(financialRisk)
                .weatherRisk(weatherRisk)
                .overallRisk(overallRisk)
                .recommendations(recommendations)
                .mlInsight(mlInsight)
                .build();
    }

    // ── Component scorers (operate on already-computed KPIs) ──

    public RiskComponent scoreOperational(OperationalKpiDTO op) {
        int score = 0;
        List<String> factors = new ArrayList<>();

        if (op.getCriticalIncidentCount() >= 2) {
            score += 30;
            factors.add("Multiple critical incidents (" + op.getCriticalIncidentCount() + ")");
        } else if (op.getCriticalIncidentCount() >= 1) {
            score += 15;
            factors.add("Critical incident present");
        }
        if (op.getIncidentCount() >= 10) {
            score += 20;
            factors.add("High incident volume (" + op.getIncidentCount() + ")");
        } else if (op.getIncidentCount() >= 5) {
            score += 10;
            factors.add("Moderate incident volume (" + op.getIncidentCount() + ")");
        }
        if (op.getSeverityIndex() > 2.5) {
            score += 20;
            factors.add("High average severity (" + op.getSeverityIndex() + ")");
        } else if (op.getSeverityIndex() > 1.5) {
            score += 10;
            factors.add("Elevated average severity");
        }
        if (op.getRecurrenceRate() > 30) {
            score += 15;
            factors.add("High recurrence rate (" + op.getRecurrenceRate() + "%)");
        }
        if (op.getAvailability() < 90) {
            score += 15;
            factors.add("Low availability (" + op.getAvailability() + "%)");
        } else if (op.getAvailability() < 95) {
            score += 8;
            factors.add("Reduced availability (" + op.getAvailability() + "%)");
        }

        score = Math.min(score, 100);
        return new RiskComponent(levelFromScore(score), score, factors);
    }

    public RiskComponent scoreFinancial(FinancialKpiDTO fin) {
        int score = 0;
        List<String> factors = new ArrayList<>();

        double absVariance = Math.abs(fin.getVariancePercent());
        if (absVariance > 15) {
            score += 40;
            factors.add("High budget variance (" + fin.getVariancePercent() + "%)");
        } else if (absVariance > 5) {
            score += 20;
            factors.add("Moderate budget variance (" + fin.getVariancePercent() + "%)");
        }
        if (fin.getBudget() > 0 && fin.getReal() > fin.getBudget()) {
            score += 20;
            factors.add("Over budget (real " + fin.getReal() + " > budget " + fin.getBudget() + ")");
        }
        if (fin.getBudget() > 0 && fin.getForecastNextMonth() > fin.getBudget()) {
            score += 10;
            factors.add("Next-month forecast exceeds current budget");
        }

        score = Math.min(score, 100);
        return new RiskComponent(levelFromScore(score), score, factors);
    }

    private RiskComponent scoreWeather(Long siteId) {
        try {
            WeatherRiskAssessmentDto weather = weatherRiskService.getLatestAssessment(siteId);
            if (weather == null || weather.getRiskScore() == null) {
                return new RiskComponent("LOW", 0, List.of("No weather assessment available"));
            }
            int score = Math.max(0, Math.min(100, weather.getRiskScore()));
            String level = weather.getRiskLevel() != null
                    ? normalizeWeatherLevel(weather.getRiskLevel().name())
                    : levelFromScore(score);
            List<String> factors = weather.getRiskFactors() != null ? weather.getRiskFactors() : List.of();
            return new RiskComponent(level, score, factors);
        } catch (Exception e) {
            log.warn("Weather risk unavailable for site {}: {}", siteId, e.getMessage());
            return new RiskComponent("LOW", 0, List.of("Weather assessment unavailable"));
        }
    }

    private MlInsight tryMlRisk(Long siteId, int year, int month) {
        try {
            MlPredictionService.RiskPredictionResult result = mlPredictionService.predictRisk(siteId, year, month);
            if (result != null && result.getRiskClass() != null) {
                return new MlInsight(result.getRiskClass());
            }
        } catch (Exception e) {
            log.debug("ML risk prediction unavailable for site {}: {}", siteId, e.getMessage());
        }
        return null;
    }

    private List<String> buildRecommendations(RiskComponent operational, RiskComponent financial,
                                              RiskComponent weather, MlInsight ml) {
        List<String> recommendations = new ArrayList<>();
        if ("HIGH".equals(operational.level())) {
            recommendations.add("Operational: prioritise incident resolution and reinforce preventive maintenance.");
        }
        if ("HIGH".equals(financial.level())) {
            recommendations.add("Financial: review budget variance and tighten expense control.");
        }
        if ("HIGH".equals(weather.level())) {
            recommendations.add("Weather: apply site protection measures for the forecast conditions.");
        }
        if (ml != null && ml.predictedRiskClass() != null && ml.predictedRiskClass().toUpperCase().contains("HIGH")) {
            recommendations.add("ML model forecasts elevated risk next month; plan mitigation ahead of time.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("All risk indicators within acceptable range; continue standard monitoring.");
        }
        return recommendations;
    }

    private String levelFromScore(int score) {
        if (score >= 60) return "HIGH";
        if (score >= 30) return "MEDIUM";
        return "LOW";
    }

    /** Map the weather engine's 4-level scale onto the decision layer's 3-level scale. */
    private String normalizeWeatherLevel(String weatherLevel) {
        return "CRITICAL".equalsIgnoreCase(weatherLevel) ? "HIGH" : weatherLevel;
    }
}
