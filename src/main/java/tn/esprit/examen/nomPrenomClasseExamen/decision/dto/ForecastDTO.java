package tn.esprit.examen.nomPrenomClasseExamen.decision.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Decision Layer output: a short-term cost forecast built from the Analytics Layer
 * (financial cost trend + operational context). It does not recompute costs or budgets — it reads
 * the financial/operational KPIs and projects them.
 */
@Data
@Builder
public class ForecastDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    /**
     * Deterministic baseline forecast for next month's totalRealCost, in the reporting currency.
     *
     * <p>Derived from the average of the most recent available months of actual historical
     * {@code totalRealCost} (operational cost + manual expenses) — never from budget. {@code null}
     * when the site has no historical actual-cost data yet (never fabricated as zero).
     *
     * <p>This is the rule-based baseline; the ML (XGBoost) prediction remains the advanced
     * forecast and is exposed separately. Compare against budget via the financial KPI's own
     * {@code budget} field, not derived from this value.
     */
    private Double nextMonthCostForecast;

    /** RISING / STABLE / DECLINING, derived from the year cost trend. */
    private String trendDirection;
    private double trendPercent;

    /** LOW / MEDIUM / HIGH, based on how much history the trend contains. */
    private String confidence;

    private List<String> drivers;
}
