package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FxRateUnavailableException;
import tn.esprit.examen.nomPrenomClasseExamen.dto.FinanceKpiDto;
import tn.esprit.examen.nomPrenomClasseExamen.services.FinanceKpiService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The legacy {@code FinanceKpiService} is now a thin adapter over {@link FinancialAnalyticsService}.
 * It must faithfully map the canonical DTO onto the legacy {@link FinanceKpiDto} field names and
 * propagate FX failures rather than inventing a value.
 */
@ExtendWith(MockitoExtension.class)
class FinanceKpiServiceParityTest {

    @Mock
    private FinancialAnalyticsService financialAnalyticsService;

    private FinanceKpiService service;

    @BeforeEach
    void setUp() {
        service = new FinanceKpiService(financialAnalyticsService);
    }

    @Test
    void mapsCanonicalKpiOntoLegacyShape() {
        when(financialAnalyticsService.getFinancialKpi(1L, 2024, 6)).thenReturn(
                FinancialKpiDTO.builder()
                        .budget(1000.0)
                        .totalRealCost(500.0)
                        .budgetVariance(-500.0)
                        .budgetVariancePercent(-50.0)
                        .forecastNextMonth(1000.0)
                        .build());

        FinanceKpiDto dto = service.getKpi(1L, 2024, 6);

        assertThat(dto.getBudgetEur()).isEqualTo(1000.0);
        assertThat(dto.getRealEur()).isEqualTo(500.0);
        assertThat(dto.getVarianceEur()).isEqualTo(-500.0);
        assertThat(dto.getVariancePercent()).isEqualTo(-50.0);
        assertThat(dto.getForecastEur()).isEqualTo(1000.0);
        assertThat(dto.getVarianceForecastEur()).isEqualTo(-500.0);   // 500 - 1000
        assertThat(dto.getRiskLevel()).isEqualTo("HIGH");             // |−50%| ≥ 15
    }

    @Test
    void zeroBudgetGivesNullPercentAndUnknownRisk() {
        when(financialAnalyticsService.getFinancialKpi(1L, 2024, 6)).thenReturn(
                FinancialKpiDTO.builder()
                        .budget(0.0)
                        .totalRealCost(300.0)
                        .budgetVariance(300.0)
                        .budgetVariancePercent(null)
                        .forecastNextMonth(0.0)
                        .build());

        FinanceKpiDto dto = service.getKpi(1L, 2024, 6);

        assertThat(dto.getVariancePercent()).isNull();
        assertThat(dto.getVarianceForecastPercent()).isNull();
        assertThat(dto.getRiskLevel()).isEqualTo("UNKNOWN");
    }

    @Test
    void propagatesFxFailureFromTheAnalyticsLayer() {
        when(financialAnalyticsService.getFinancialKpi(1L, 2024, 6))
                .thenThrow(new FxRateUnavailableException("FxRate not found: missing FX rate USD->EUR for 2024-06"));

        assertThatThrownBy(() -> service.getKpi(1L, 2024, 6))
                .isInstanceOf(FxRateUnavailableException.class)
                .hasMessageContaining("FxRate not found");
    }
}
