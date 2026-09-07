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
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.GlobalRiskDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.RiskEngine;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MlPredictionService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherRiskAssessmentDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.RiskLevel;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEngineTest {

    @Mock
    private OperationalAnalyticsService operationalAnalytics;
    @Mock
    private FinancialAnalyticsService financialAnalytics;
    @Mock
    private WeatherRiskService weatherRiskService;
    @Mock
    private MlPredictionService mlPredictionService;
    @InjectMocks
    private RiskEngine riskEngine;

    @Test
    void aggregatesHighRiskAcrossAllDimensions() {
        OperationalKpiDTO op = OperationalKpiDTO.builder()
                .criticalIncidentCount(2).incidentCount(12)
                .severityIndex(3.0).recurrenceRate(40.0).availability(85.0)
                .build();
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .variancePercent(20.0).real(1200).budget(1000).forecastNextMonth(1300)
                .build();

        when(operationalAnalytics.getOperationalKpi(1L, 2024, 6)).thenReturn(op);
        when(financialAnalytics.getFinancialKpi(1L, 2024, 6)).thenReturn(fin);
        when(weatherRiskService.getLatestAssessment(1L)).thenReturn(WeatherRiskAssessmentDto.builder()
                .riskScore(80).riskLevel(RiskLevel.HIGH).riskFactors(List.of("Storm warning")).build());
        when(mlPredictionService.predictRisk(eq(1L), anyInt(), anyInt()))
                .thenReturn(MlPredictionService.RiskPredictionResult.builder().riskClass("HIGH_RISK").build());

        GlobalRiskDTO dto = riskEngine.assessGlobalRisk(1L, 2024, 6);

        assertThat(dto.getOperationalRisk().level()).isEqualTo("HIGH");
        assertThat(dto.getOperationalRisk().score()).isEqualTo(100);
        assertThat(dto.getFinancialRisk().level()).isEqualTo("HIGH");
        assertThat(dto.getFinancialRisk().score()).isEqualTo(70);
        assertThat(dto.getWeatherRisk().level()).isEqualTo("HIGH");
        assertThat(dto.getWeatherRisk().score()).isEqualTo(80);
        assertThat(dto.getOverallRisk().level()).isEqualTo("HIGH");
        assertThat(dto.getOverallRisk().score()).isEqualTo(85); // round(40 + 24.5 + 20)
        assertThat(dto.getMlInsight().predictedRiskClass()).isEqualTo("HIGH_RISK");
        assertThat(dto.getRecommendations()).hasSize(4);
    }

    @Test
    void degradesGracefullyWhenWeatherAndMlUnavailable() {
        OperationalKpiDTO op = OperationalKpiDTO.builder()
                .criticalIncidentCount(0).incidentCount(1)
                .severityIndex(1.0).recurrenceRate(0.0).availability(100.0)
                .build();
        FinancialKpiDTO fin = FinancialKpiDTO.builder()
                .variancePercent(0.0).real(0).budget(0).forecastNextMonth(0)
                .build();

        when(operationalAnalytics.getOperationalKpi(2L, 2024, 3)).thenReturn(op);
        when(financialAnalytics.getFinancialKpi(2L, 2024, 3)).thenReturn(fin);
        when(weatherRiskService.getLatestAssessment(2L)).thenThrow(new RuntimeException("no weather data"));
        when(mlPredictionService.predictRisk(eq(2L), anyInt(), anyInt())).thenThrow(new RuntimeException("ML down"));

        GlobalRiskDTO dto = riskEngine.assessGlobalRisk(2L, 2024, 3);

        assertThat(dto.getOverallRisk().level()).isEqualTo("LOW");
        assertThat(dto.getOverallRisk().score()).isZero();
        assertThat(dto.getWeatherRisk().level()).isEqualTo("LOW");
        assertThat(dto.getMlInsight()).isNull();
        assertThat(dto.getRecommendations()).containsExactly(
                "All risk indicators within acceptable range; continue standard monitoring.");
    }
}
