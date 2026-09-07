package tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Business "at a glance" view for a site/month, composed by the Dashboard layer from the Analytics
 * (operational + financial), Decision (global risk) and Weather layers.
 */
@Data
@Builder
public class DashboardOverviewDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    private String overallRiskLevel;
    private int overallRiskScore;

    private OperationalSummary operational;
    private FinancialSummary financial;
    private WeatherSummary weather;

    private List<String> recommendations;

    /** One-line human summary of the site status. */
    private String headline;

    public record OperationalSummary(long incidentCount, long criticalIncidentCount,
                                     double availability, double totalOperationalCost) {
    }

    public record FinancialSummary(double budget, double real, double variancePercent) {
    }

    public record WeatherSummary(String level, int score) {
    }
}
