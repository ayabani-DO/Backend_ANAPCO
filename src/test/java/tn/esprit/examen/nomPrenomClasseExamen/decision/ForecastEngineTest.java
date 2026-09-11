package tn.esprit.examen.nomPrenomClasseExamen.decision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.ForecastDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.ForecastEngine;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastEngineTest {

    @Mock
    private OperationalAnalyticsService operationalAnalytics;
    @Mock
    private FinancialAnalyticsService financialAnalytics;
    @InjectMocks
    private ForecastEngine forecastEngine;

    private static List<FinancialKpiDTO.MonthlyCost> trend(double... perMonth) {
        List<FinancialKpiDTO.MonthlyCost> list = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            double v = m <= perMonth.length ? perMonth[m - 1] : 0.0;
            list.add(new FinancialKpiDTO.MonthlyCost(String.format("2024-%02d", m), v));
        }
        return list;
    }

    @Test
    void projectsRisingTrendWithDrivers() {
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .variancePercent(20.0)
                .forecastNextMonth(250.0) // legacy budget-derived field — must NOT be used as the forecast
                .costTrend(trend(100, 120, 140, 160, 100, 200)) // months 1..6
                .build();
        OperationalKpiDTO op = OperationalKpiDTO.builder().criticalIncidentCount(1).build();

        when(financialAnalytics.getFinancialKpi(1L, 2024, 6)).thenReturn(fin);
        when(operationalAnalytics.getOperationalKpi(1L, 2024, 6)).thenReturn(op);

        ForecastDTO dto = forecastEngine.forecast(1L, 2024, 6);

        // Average of the last 3 available months of ACTUAL totalRealCost (months 4..6: 160, 100, 200)
        // — deliberately different from the legacy budget-derived forecastNextMonth (250.0) above.
        assertThat(dto.getNextMonthCostForecast()).isEqualTo(153.33);
        assertThat(dto.getTrendDirection()).isEqualTo("RISING"); // 200 vs 100 = +100%
        assertThat(dto.getTrendPercent()).isEqualTo(100.0);
        assertThat(dto.getConfidence()).isEqualTo("HIGH");       // 6 months with data
        assertThat(dto.getDrivers()).contains(
                "Rising monthly cost trend (100.0%)",
                "Recent critical incidents (1)",
                "Significant budget variance (20.0%)");
    }

    @Test
    void stableProfileWhenNoPriorMonth() {
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .variancePercent(0.0)
                .forecastNextMonth(100.0)
                .costTrend(trend(100)) // only January has data
                .build();
        OperationalKpiDTO op = OperationalKpiDTO.builder().criticalIncidentCount(0).build();

        when(financialAnalytics.getFinancialKpi(1L, 2024, 1)).thenReturn(fin);
        when(operationalAnalytics.getOperationalKpi(1L, 2024, 1)).thenReturn(op);

        ForecastDTO dto = forecastEngine.forecast(1L, 2024, 1);

        assertThat(dto.getTrendDirection()).isEqualTo("STABLE");
        assertThat(dto.getConfidence()).isEqualTo("LOW");
        assertThat(dto.getDrivers()).containsExactly("Stable cost profile");
        // Requirement: with limited history (one month), the forecast uses the available month(s).
        assertThat(dto.getNextMonthCostForecast()).isEqualTo(100.0);
    }

    // ── FIX 3: rule-based forecast must come from historical actual totalRealCost, not budget ──

    @Test
    void forecastIsDerivedFromHistoricalActualCostNotBudget() {
        // Budget-derived legacy field deliberately set far away from the actual cost history,
        // to prove the new forecast never reads it.
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .forecastNextMonth(99999.0)
                .costTrend(trend(200, 300, 400)) // months 1..3
                .build();
        OperationalKpiDTO op = OperationalKpiDTO.builder().build();

        when(financialAnalytics.getFinancialKpi(1L, 2024, 3)).thenReturn(fin);
        when(operationalAnalytics.getOperationalKpi(1L, 2024, 3)).thenReturn(op);

        ForecastDTO dto = forecastEngine.forecast(1L, 2024, 3);

        assertThat(dto.getNextMonthCostForecast()).isEqualTo(300.0); // avg(200, 300, 400)
        assertThat(dto.getNextMonthCostForecast()).isNotEqualTo(99999.0);
    }

    @Test
    void multipleMonthsProduceDeterministicRecentHistoryAverage() {
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .costTrend(trend(50, 60, 70, 80, 90, 1000, 1000, 1000)) // months 1..8
                .build();
        OperationalKpiDTO op = OperationalKpiDTO.builder().build();

        when(financialAnalytics.getFinancialKpi(1L, 2024, 5)).thenReturn(fin);
        when(operationalAnalytics.getOperationalKpi(1L, 2024, 5)).thenReturn(op);

        ForecastDTO first = forecastEngine.forecast(1L, 2024, 5);
        ForecastDTO second = forecastEngine.forecast(1L, 2024, 5);

        // Average of months 3..5 (70, 80, 90) — later months (6..8) must not leak into a month-5 forecast.
        assertThat(first.getNextMonthCostForecast()).isEqualTo(80.0);
        assertThat(second.getNextMonthCostForecast()).isEqualTo(first.getNextMonthCostForecast()); // deterministic
    }

    @Test
    void noHistoricalActualCostDataReturnsNullNotZero() {
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .forecastNextMonth(500.0) // budget present, but no actual-cost history at all
                .costTrend(trend()) // every month is 0 (no data)
                .build();
        OperationalKpiDTO op = OperationalKpiDTO.builder().build();

        when(financialAnalytics.getFinancialKpi(1L, 2024, 6)).thenReturn(fin);
        when(operationalAnalytics.getOperationalKpi(1L, 2024, 6)).thenReturn(op);

        ForecastDTO dto = forecastEngine.forecast(1L, 2024, 6);

        assertThat(dto.getNextMonthCostForecast()).isNull();
    }
}
