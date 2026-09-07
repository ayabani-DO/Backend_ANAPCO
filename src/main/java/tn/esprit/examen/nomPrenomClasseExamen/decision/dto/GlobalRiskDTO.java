package tn.esprit.examen.nomPrenomClasseExamen.decision.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Decision Layer output: a consolidated risk picture for a site, aggregated from the Analytics Layer
 * (operational + financial), the weather risk engine, and — when available — the ML risk model.
 *
 * <p>The Decision Layer <b>scores</b> already-computed KPIs; it does not recompute them.
 */
@Data
@Builder
public class GlobalRiskDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    private RiskComponent operationalRisk;
    private RiskComponent financialRisk;
    private RiskComponent weatherRisk;
    private RiskComponent overallRisk;

    private List<String> recommendations;

    /** Present only when the ML risk model answered; null otherwise. */
    private MlInsight mlInsight;

    /** One risk dimension: a level (LOW/MEDIUM/HIGH), a 0..100 score and the driving factors. */
    public record RiskComponent(String level, int score, List<String> factors) {
    }

    public record MlInsight(String predictedRiskClass) {
    }
}
