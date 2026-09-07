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

    /** Forecast cost for the next month, in EUR (from the financial KPI). */
    private double nextMonthCostForecast;

    /** RISING / STABLE / DECLINING, derived from the year cost trend. */
    private String trendDirection;
    private double trendPercent;

    /** LOW / MEDIUM / HIGH, based on how much history the trend contains. */
    private String confidence;

    private List<String> drivers;
}
