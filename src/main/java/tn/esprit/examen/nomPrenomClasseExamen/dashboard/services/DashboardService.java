package tn.esprit.examen.nomPrenomClasseExamen.dashboard.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.EquipmentAnalyticsDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.EquipmentAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardAiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardEquipmentDTO;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardFinancialDTO;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardOverviewDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.ForecastDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.GlobalRiskDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.ForecastEngine;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.RiskEngine;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MlPredictionService;

/**
 * Dashboard layer — orchestrates the Analytics, Decision, AI (ML) and Weather layers into a small set
 * of business-oriented views, so the frontend gets consolidated information in one call. It composes
 * existing services; it holds no business calculation of its own and touches no repository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final OperationalAnalyticsService operationalAnalytics;
    private final FinancialAnalyticsService financialAnalytics;
    private final EquipmentAnalyticsService equipmentAnalytics;
    private final RiskEngine riskEngine;
    private final ForecastEngine forecastEngine;
    private final MlPredictionService mlPredictionService;

    /** Site "at a glance": operational + financial + global risk + weather headline. */
    public DashboardOverviewDTO getOverview(Long siteId, int year, int month) {
        OperationalKpiDTO op = operationalAnalytics.getOperationalKpi(siteId, year, month);
        FinancialKpiDTO fin = financialAnalytics.getFinancialKpi(siteId, year, month);
        GlobalRiskDTO risk = riskEngine.assessGlobalRisk(siteId, year, month, op, fin);

        return DashboardOverviewDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .overallRiskLevel(risk.getOverallRisk().level())
                .overallRiskScore(risk.getOverallRisk().score())
                .operational(new DashboardOverviewDTO.OperationalSummary(
                        op.getIncidentCount(), op.getCriticalIncidentCount(),
                        op.getAvailability(), op.getTotalOperationalCost()))
                .financial(new DashboardOverviewDTO.FinancialSummary(
                        fin.getBudget(), fin.getReal(), fin.getVariancePercent()))
                .weather(new DashboardOverviewDTO.WeatherSummary(
                        risk.getWeatherRisk().level(), risk.getWeatherRisk().score()))
                .recommendations(risk.getRecommendations())
                .headline(buildHeadline(siteId, risk, op, fin))
                .build();
    }

    /** Financial deep view: financial KPIs + rule-based forecast + financial risk level. */
    public DashboardFinancialDTO getFinancial(Long siteId, int year, int month) {
        FinancialKpiDTO fin = financialAnalytics.getFinancialKpi(siteId, year, month);
        OperationalKpiDTO op = operationalAnalytics.getOperationalKpi(siteId, year, month);
        ForecastDTO forecast = forecastEngine.forecast(siteId, year, month, fin, op);

        return DashboardFinancialDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .financials(fin)
                .forecast(forecast)
                .financialRiskLevel(riskEngine.scoreFinancial(fin).level())
                .build();
    }

    /** Equipment health card + a decision-oriented attention flag and priority action. */
    public DashboardEquipmentDTO getEquipment(Long equipmentId) {
        EquipmentAnalyticsDTO equipment = equipmentAnalytics.getEquipmentAnalytics(equipmentId);
        boolean attentionRequired = "HIGH".equals(equipment.getRiskLevel()) || equipment.getHealthScore() < 50;
        return DashboardEquipmentDTO.builder()
                .equipment(equipment)
                .attentionRequired(attentionRequired)
                .priorityAction(equipment.getRulAction())
                .build();
    }

    /** AI view: ML cost/risk predictions (defensive) with a rule-based forecast fallback. */
    public DashboardAiDTO getAi(Long siteId, int year, int month) {
        ForecastDTO ruleBasedForecast = forecastEngine.forecast(siteId, year, month);

        Double mlCost = null;
        String mlRiskClass = null;
        boolean mlAvailable = false;
        try {
            MlPredictionService.CostPredictionResult cost = mlPredictionService.predictCost(siteId, year, month);
            MlPredictionService.RiskPredictionResult risk = mlPredictionService.predictRisk(siteId, year, month);
            mlCost = cost != null ? cost.getPredictedNextMonthCostEur() : null;
            mlRiskClass = risk != null ? risk.getRiskClass() : null;
            mlAvailable = mlCost != null || mlRiskClass != null;
        } catch (Exception e) {
            log.debug("ML predictions unavailable for site {}: {}", siteId, e.getMessage());
        }

        return DashboardAiDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .mlAvailable(mlAvailable)
                .mlPredictedNextMonthCostEur(mlCost)
                .mlPredictedRiskClass(mlRiskClass)
                .ruleBasedForecast(ruleBasedForecast)
                .build();
    }

    private String buildHeadline(Long siteId, GlobalRiskDTO risk, OperationalKpiDTO op, FinancialKpiDTO fin) {
        return String.format(
                "Site %d: overall risk %s (%d/100). %d incidents (%d critical), availability %.1f%%. "
                        + "Budget variance %.1f%%.",
                siteId, risk.getOverallRisk().level(), risk.getOverallRisk().score(),
                op.getIncidentCount(), op.getCriticalIncidentCount(), op.getAvailability(),
                fin.getVariancePercent());
    }
}
