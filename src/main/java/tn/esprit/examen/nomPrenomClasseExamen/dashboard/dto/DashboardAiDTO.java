package tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.ForecastDTO;

/**
 * Business AI view for a site/month: the ML model outputs (cost forecast + risk classification) with
 * a rule-based Decision-layer forecast as a fallback/comparison. Degrades gracefully when the ML
 * microservice is unavailable ({@code mlAvailable = false}).
 */
@Data
@Builder
public class DashboardAiDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    private boolean mlAvailable;

    /** ML model 1: predicted next-month total cost (EUR); null when unavailable. */
    private Double mlPredictedNextMonthCostEur;

    /** ML model 2: predicted risk class; null when unavailable. */
    private String mlPredictedRiskClass;

    /** Rule-based forecast from the Decision layer (always available). */
    private ForecastDTO ruleBasedForecast;
}
