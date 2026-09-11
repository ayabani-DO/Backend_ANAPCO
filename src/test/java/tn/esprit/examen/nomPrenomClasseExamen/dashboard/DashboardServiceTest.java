package tn.esprit.examen.nomPrenomClasseExamen.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.services.DashboardService;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.ForecastDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.GlobalRiskDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.ForecastEngine;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.RiskEngine;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MlPredictionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private OperationalAnalyticsService operationalAnalytics;
    @Mock private FinancialAnalyticsService financialAnalytics;
    @Mock private EquipmentAnalyticsService equipmentAnalytics;
    @Mock private RiskEngine riskEngine;
    @Mock private ForecastEngine forecastEngine;
    @Mock private MlPredictionService mlPredictionService;
    @InjectMocks private DashboardService dashboardService;

    @Test
    void overviewComposesAnalyticsDecisionAndWeather() {
        OperationalKpiDTO op = OperationalKpiDTO.builder()
                .incidentCount(3).criticalIncidentCount(1).availability(88.0).totalOperationalCost(2000).build();
        FinancialKpiDTO fin = FinancialKpiDTO.builder().budget(1000).real(1200).variancePercent(20.0).build();
        GlobalRiskDTO risk = GlobalRiskDTO.builder()
                .overallRisk(new GlobalRiskDTO.RiskComponent("MEDIUM", 45, List.of()))
                .weatherRisk(new GlobalRiskDTO.RiskComponent("LOW", 10, List.of()))
                .recommendations(List.of("Review budget variance"))
                .build();

        when(operationalAnalytics.getOperationalKpi(1L, 2024, 6)).thenReturn(op);
        when(financialAnalytics.getFinancialKpi(1L, 2024, 6)).thenReturn(fin);
        when(riskEngine.assessGlobalRisk(1L, 2024, 6, op, fin)).thenReturn(risk);

        DashboardOverviewDTO dto = dashboardService.getOverview(1L, 2024, 6);

        assertThat(dto.getOverallRiskLevel()).isEqualTo("MEDIUM");
        assertThat(dto.getOverallRiskScore()).isEqualTo(45);
        assertThat(dto.getOperational().incidentCount()).isEqualTo(3);
        assertThat(dto.getOperational().availability()).isEqualTo(88.0);
        assertThat(dto.getFinancial().variancePercent()).isEqualTo(20.0);
        assertThat(dto.getWeather().level()).isEqualTo("LOW");
        assertThat(dto.getRecommendations()).containsExactly("Review budget variance");
        assertThat(dto.getHeadline()).contains("overall risk MEDIUM (45/100)", "3 incidents");
    }

    @Test
    void financialViewAddsForecastAndRiskLevel() {
        FinancialKpiDTO fin = FinancialKpiDTO.builder().budget(1000).real(1200).variancePercent(20.0).build();
        OperationalKpiDTO op = OperationalKpiDTO.builder().criticalIncidentCount(0).build();
        ForecastDTO forecast = ForecastDTO.builder().trendDirection("RISING").nextMonthCostForecast(1300.0).build();

        when(financialAnalytics.getFinancialKpi(1L, 2024, 6)).thenReturn(fin);
        when(operationalAnalytics.getOperationalKpi(1L, 2024, 6)).thenReturn(op);
        when(forecastEngine.forecast(1L, 2024, 6, fin, op)).thenReturn(forecast);
        when(riskEngine.scoreFinancial(fin)).thenReturn(new GlobalRiskDTO.RiskComponent("HIGH", 70, List.of()));

        DashboardFinancialDTO dto = dashboardService.getFinancial(1L, 2024, 6);

        assertThat(dto.getFinancials()).isSameAs(fin);
        assertThat(dto.getForecast()).isSameAs(forecast);
        assertThat(dto.getFinancialRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void equipmentViewFlagsAttentionForHighRisk() {
        EquipmentAnalyticsDTO equipment = EquipmentAnalyticsDTO.builder()
                .riskLevel("HIGH").healthScore(40).rulAction("Plan urgent corrective maintenance.").build();
        when(equipmentAnalytics.getEquipmentAnalytics(5L)).thenReturn(equipment);

        DashboardEquipmentDTO dto = dashboardService.getEquipment(5L);

        assertThat(dto.isAttentionRequired()).isTrue();
        assertThat(dto.getPriorityAction()).isEqualTo("Plan urgent corrective maintenance.");
        assertThat(dto.getEquipment()).isSameAs(equipment);
    }

    @Test
    void aiViewExposesMlPredictionsWhenAvailable() {
        ForecastDTO ruleBased = ForecastDTO.builder().trendDirection("STABLE").build();
        when(forecastEngine.forecast(1L, 2024, 6)).thenReturn(ruleBased);
        when(mlPredictionService.predictCost(1L, 2024, 6)).thenReturn(
                MlPredictionService.CostPredictionResult.builder().predictedNextMonthCostEur(1234.5).build());
        when(mlPredictionService.predictRisk(1L, 2024, 6)).thenReturn(
                MlPredictionService.RiskPredictionResult.builder().riskClass("MEDIUM_RISK").build());

        DashboardAiDTO dto = dashboardService.getAi(1L, 2024, 6);

        assertThat(dto.isMlAvailable()).isTrue();
        assertThat(dto.getMlPredictedNextMonthCostEur()).isEqualTo(1234.5);
        assertThat(dto.getMlPredictedRiskClass()).isEqualTo("MEDIUM_RISK");
        assertThat(dto.getRuleBasedForecast()).isSameAs(ruleBased);
    }

    @Test
    void aiViewDegradesGracefullyWhenMlDown() {
        ForecastDTO ruleBased = ForecastDTO.builder().trendDirection("STABLE").build();
        when(forecastEngine.forecast(2L, 2024, 3)).thenReturn(ruleBased);
        when(mlPredictionService.predictCost(2L, 2024, 3)).thenThrow(new RuntimeException("ML service down"));

        DashboardAiDTO dto = dashboardService.getAi(2L, 2024, 3);

        assertThat(dto.isMlAvailable()).isFalse();
        assertThat(dto.getMlPredictedNextMonthCostEur()).isNull();
        assertThat(dto.getMlPredictedRiskClass()).isNull();
        assertThat(dto.getRuleBasedForecast()).isSameAs(ruleBased);
    }
}
