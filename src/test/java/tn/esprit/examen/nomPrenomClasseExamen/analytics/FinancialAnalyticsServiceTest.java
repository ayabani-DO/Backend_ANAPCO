package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpenseCategory;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Deterministic, DB-free verification of the canonical financial formulas (site currency = EUR,
 * so no FX lookups are exercised here).
 */
@ExtendWith(MockitoExtension.class)
class FinancialAnalyticsServiceTest {

    @Mock
    private SitesRepository sitesRepository;
    @Mock
    private BudgetMonthlyRepository budgetMonthlyRepository;
    @Mock
    private ManualExpenseRepository manualExpenseRepository;
    @Mock
    private FxRateRepository fxRateRepository;

    private FinancialAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new FinancialAnalyticsService(
                sitesRepository, budgetMonthlyRepository, manualExpenseRepository,
                new CurrencyConverter(fxRateRepository));
    }

    private static Sites siteEur(long id) {
        Sites s = new Sites();
        s.setIdSite(id);
        s.setCurrencyCode("EUR");
        return s;
    }

    private static BudgetMonthly budget(double amount) {
        BudgetMonthly b = new BudgetMonthly();
        b.setAmount(amount);
        b.setCurrencyCode("EUR");
        return b;
    }

    private static ManualExpense expense(ManualExpenseCategory cat, double amount, LocalDate date) {
        ManualExpense e = new ManualExpense();
        e.setCategory(cat);
        e.setAmount(amount);
        e.setCurrencyCode("EUR");
        e.setDate(date);
        return e;
    }

    @Test
    void computesBudgetRealVarianceTrendAndCategories() {
        when(sitesRepository.findById(1L)).thenReturn(Optional.of(siteEur(1L)));
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(1L, 2024, 6))
                .thenReturn(Optional.of(budget(1000.0)));
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(1L, 2024, 7))
                .thenReturn(Optional.of(budget(1200.0)));
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(
                        expense(ManualExpenseCategory.TRAVEL, 300.0, LocalDate.of(2024, 6, 4)),
                        expense(ManualExpenseCategory.ENERGY, 200.0, LocalDate.of(2024, 6, 20)),
                        expense(ManualExpenseCategory.OTHER, 100.0, LocalDate.of(2024, 5, 15))
                ));

        FinancialKpiDTO kpi = service.getFinancialKpi(1L, 2024, 6);

        assertThat(kpi.getBudget()).isEqualTo(1000.0);
        assertThat(kpi.getReal()).isEqualTo(500.0);            // June: 300 + 200
        assertThat(kpi.getVariance()).isEqualTo(-500.0);
        assertThat(kpi.getVariancePercent()).isEqualTo(-50.0);
        assertThat(kpi.getForecastNextMonth()).isEqualTo(1200.0);
        assertThat(kpi.getCurrency()).isEqualTo("EUR");

        // Trend: 12 points, May = 100, June = 500, rest 0.
        assertThat(kpi.getCostTrend()).hasSize(12);
        assertThat(kpi.getCostTrend().get(4)).isEqualTo(new FinancialKpiDTO.MonthlyCost("2024-05", 100.0));
        assertThat(kpi.getCostTrend().get(5)).isEqualTo(new FinancialKpiDTO.MonthlyCost("2024-06", 500.0));

        // Categories over the year, highest first.
        assertThat(kpi.getTopExpenseCategories()).containsExactly(
                new FinancialKpiDTO.CategoryExpense("TRAVEL", 300.0),
                new FinancialKpiDTO.CategoryExpense("ENERGY", 200.0),
                new FinancialKpiDTO.CategoryExpense("OTHER", 100.0));
    }

    @Test
    void noBudgetYieldsZeroVarianceAndBudgetFallbackForecast() {
        when(sitesRepository.findById(2L)).thenReturn(Optional.of(siteEur(2L)));
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(eq(2L), any(), any()))
                .thenReturn(Optional.empty());
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(2L), any(), any()))
                .thenReturn(List.of());

        FinancialKpiDTO kpi = service.getFinancialKpi(2L, 2024, 3);

        assertThat(kpi.getBudget()).isZero();
        assertThat(kpi.getReal()).isZero();
        assertThat(kpi.getVariancePercent()).isZero();     // budget 0 -> guarded
        assertThat(kpi.getForecastNextMonth()).isZero();
        assertThat(kpi.getTopExpenseCategories()).isEmpty();
    }
}
