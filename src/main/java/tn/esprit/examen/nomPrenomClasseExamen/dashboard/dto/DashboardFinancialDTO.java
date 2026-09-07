package tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.ForecastDTO;

/**
 * Business financial view for a site/month: the financial KPIs (budget vs. real, trend, categories)
 * together with the Decision-layer cost forecast and a headline financial risk level.
 */
@Data
@Builder
public class DashboardFinancialDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    private FinancialKpiDTO financials;
    private ForecastDTO forecast;

    private String financialRiskLevel;
}
