package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.dto.FinanceKpiDto;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;
import tn.esprit.examen.nomPrenomClasseExamen.services.FinanceKpiService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Parity lock for {@link FinanceKpiService#getKpi} after its FX conversion is routed through the
 * shared {@link CurrencyConverter} (strict mode). Values must be identical, and the strict behaviour
 * (throw on missing rate) must be preserved.
 */
@ExtendWith(MockitoExtension.class)
class FinanceKpiServiceParityTest {

    @Mock
    private SitesRepository sitesRepository;
    @Mock
    private BudgetMonthlyRepository budgetMonthlyRepository;
    @Mock
    private ManualExpenseRepository manualExpenseRepository;
    @Mock
    private FxRateRepository fxRateRepository;

    private FinanceKpiService service;

    @BeforeEach
    void setUp() {
        service = new FinanceKpiService(
                sitesRepository, budgetMonthlyRepository, manualExpenseRepository,
                new CurrencyConverter(fxRateRepository));
    }

    private static Sites site(String currency) {
        Sites s = new Sites();
        s.setIdSite(1L);
        s.setCurrencyCode(currency);
        return s;
    }

    private static BudgetMonthly budget(double amount, String currency) {
        BudgetMonthly b = new BudgetMonthly();
        b.setAmount(amount);
        b.setCurrencyCode(currency);
        return b;
    }

    private static ManualExpense expense(double amount, String currency) {
        ManualExpense e = new ManualExpense();
        e.setAmount(amount);
        e.setCurrencyCode(currency);
        e.setDate(LocalDate.of(2024, 6, 10));
        return e;
    }

    @Test
    void financeKpiValuesAreStable_eur() {
        when(sitesRepository.findById(1L)).thenReturn(Optional.of(site("EUR")));
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(1L, 2024, 6))
                .thenReturn(Optional.of(budget(1000.0, "EUR")));
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(expense(300.0, "EUR"), expense(200.0, "EUR")));

        FinanceKpiDto dto = service.getKpi(1L, 2024, 6);

        assertThat(dto.getBudgetEur()).isEqualTo(1000.0);
        assertThat(dto.getRealEur()).isEqualTo(500.0);
        assertThat(dto.getVarianceEur()).isEqualTo(-500.0);
        assertThat(dto.getVariancePercent()).isEqualTo(-50.0);
        assertThat(dto.getForecastEur()).isEqualTo(1000.0);
        assertThat(dto.getRiskLevel()).isEqualTo("HIGH"); // |−50%| ≥ 15
    }

    @Test
    void financeKpiStillThrowsOnMissingFxRate() {
        when(sitesRepository.findById(1L)).thenReturn(Optional.of(site("USD")));
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(1L, 2024, 6))
                .thenReturn(Optional.of(budget(100.0, "USD")));
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(2024), eq(6), eq("USD"), eq("EUR"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getKpi(1L, 2024, 6))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FxRate not found");
    }
}
