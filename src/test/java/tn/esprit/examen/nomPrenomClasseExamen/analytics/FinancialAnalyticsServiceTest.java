package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpenseCategory;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;
import tn.esprit.examen.nomPrenomClasseExamen.services.FxRateService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Canonical financial formulas: totalRealCost = operationalCost + manualExpenses,
 * budgetVariance = totalRealCost - budget, budgetVariancePercent = null when budget == 0.
 */
@ExtendWith(MockitoExtension.class)
class FinancialAnalyticsServiceTest {

    @Mock private SitesRepository sitesRepository;
    @Mock private BudgetMonthlyRepository budgetMonthlyRepository;
    @Mock private ManualExpenseRepository manualExpenseRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private FxRateRepository fxRateRepository;

    private FinancialAnalyticsService service;

    @BeforeEach
    void setUp() {
        CurrencyConverter cc = new CurrencyConverter(new FxRateService(fxRateRepository, null));
        OperationalAnalyticsService op =
                new OperationalAnalyticsService(incidentRepository, maintenanceRepository, sitesRepository, cc);
        service = new FinancialAnalyticsService(
                sitesRepository, budgetMonthlyRepository, manualExpenseRepository, cc, op);

        lenient().when(incidentRepository.findBySitesIdSiteAndDateBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(any(), any(), any())).thenReturn(List.of());
    }

    private static Date d(int y, int m, int day) {
        return Date.from(LocalDate.of(y, m, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Sites siteEur(long id) {
        Sites s = new Sites();
        s.setIdSite(id);
        s.setCurrencyCode("EUR");
        lenient().when(sitesRepository.findById(id)).thenReturn(Optional.of(s));
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

    private static Incident incident(double cost, Date date) {
        Incident i = new Incident();
        i.setCostReal(cost);
        i.setEtatIncident(EtatIncident.OPEN);
        i.setSeverityCode(SeverityCode.MEDIUM);
        i.setDate(date);
        return i;
    }

    private static Maintenance maint(StatusMaintenace status, double cost, Date date) {
        Maintenance m = new Maintenance();
        m.setTypeMaintenance(TypeMaintenance.CORRECTIVE);
        m.setStatusMaintenance(status);
        m.setCostReal(cost);
        m.setDate(date);
        return m;
    }

    @Test
    void computesBudgetRealVarianceTrendAndCategories() {
        siteEur(1L);
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

        assertThat(kpi.getCurrency()).isEqualTo("EUR");
        assertThat(kpi.getBudget()).isEqualTo(1000.0);
        assertThat(kpi.getOperationalCost()).isZero();
        assertThat(kpi.getManualExpenses()).isEqualTo(500.0);          // June: 300 + 200
        assertThat(kpi.getTotalRealCost()).isEqualTo(500.0);
        assertThat(kpi.getBudgetVariance()).isEqualTo(-500.0);
        assertThat(kpi.getBudgetVariancePercent()).isEqualTo(-50.0);
        assertThat(kpi.isFxComplete()).isTrue();
        assertThat(kpi.getForecastNextMonth()).isEqualTo(1200.0);

        // legacy fields mirror the canonical ones
        assertThat(kpi.getReal()).isEqualTo(500.0);
        assertThat(kpi.getVariance()).isEqualTo(-500.0);
        assertThat(kpi.getVariancePercent()).isEqualTo(-50.0);

        // Trend: 12 points, May = 100, June = 500, rest 0.
        assertThat(kpi.getCostTrend()).hasSize(12);
        assertThat(kpi.getCostTrend().get(4)).isEqualTo(new FinancialKpiDTO.MonthlyCost("2024-05", 100.0));
        assertThat(kpi.getCostTrend().get(5)).isEqualTo(new FinancialKpiDTO.MonthlyCost("2024-06", 500.0));

        assertThat(kpi.getTopExpenseCategories()).containsExactly(
                new FinancialKpiDTO.CategoryExpense("TRAVEL", 300.0),
                new FinancialKpiDTO.CategoryExpense("ENERGY", 200.0),
                new FinancialKpiDTO.CategoryExpense("OTHER", 100.0));
    }

    @Test
    void totalRealCostAddsOperationalCostToManualExpenses() {
        siteEur(1L);
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(1L, 2024, 6))
                .thenReturn(Optional.of(budget(200.0)));
        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(incident(100.0, d(2024, 6, 3))));
        when(maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(
                        maint(StatusMaintenace.DONE, 50.0, d(2024, 6, 5)),
                        maint(StatusMaintenace.PLANNED, 80.0, d(2024, 6, 9))));   // planned: must NOT inflate real cost
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(expense(ManualExpenseCategory.OTHER, 25.0, LocalDate.of(2024, 6, 12))));

        FinancialKpiDTO kpi = service.getFinancialKpi(1L, 2024, 6);

        assertThat(kpi.getIncidentRealCost()).isEqualTo(100.0);
        assertThat(kpi.getRealisedMaintenanceCost()).isEqualTo(50.0);
        assertThat(kpi.getPlannedMaintenanceCost()).isEqualTo(80.0);
        assertThat(kpi.getOperationalCost()).isEqualTo(150.0);         // 100 + 50 (planned excluded)
        assertThat(kpi.getManualExpenses()).isEqualTo(25.0);
        assertThat(kpi.getTotalRealCost()).isEqualTo(175.0);           // 150 + 25
        assertThat(kpi.getBudgetVariance()).isEqualTo(-25.0);          // 175 - 200
        assertThat(kpi.getBudgetVariancePercent()).isEqualTo(-12.5);
    }

    @Test
    void zeroBudgetYieldsNullVariancePercentNotZero() {
        siteEur(2L);
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(eq(2L), any(), any()))
                .thenReturn(Optional.empty());
        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(2L), any(), any()))
                .thenReturn(List.of(incident(300.0, d(2024, 3, 3))));
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(2L), any(), any()))
                .thenReturn(List.of());

        FinancialKpiDTO kpi = service.getFinancialKpi(2L, 2024, 3);

        assertThat(kpi.getBudget()).isZero();
        assertThat(kpi.getTotalRealCost()).isEqualTo(300.0);
        assertThat(kpi.getBudgetVariance()).isEqualTo(300.0);
        assertThat(kpi.getBudgetVariancePercent()).isNull();          // NOT 0.0
        assertThat(kpi.getVariancePercent()).isZero();                // legacy primitive stays 0.0
        assertThat(kpi.getTopExpenseCategories()).isEmpty();
    }

    @Test
    void multiCurrencyValuesAreNormalisedThroughCurrencyConverter() {
        // Site in GBP; stored EUR->GBP = 0.80 for June 2024.
        Sites gbp = new Sites();
        gbp.setIdSite(3L);
        gbp.setCurrencyCode("GBP");
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(gbp));
        when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(2024), eq(6), eq("EUR"), eq("GBP")))
                .thenReturn(Optional.of(fx(2024, 6, "GBP", 0.80)));

        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(3L, 2024, 6))
                .thenReturn(Optional.of(budgetGbp(800.0)));            // 800 GBP -> 1000 EUR
        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(3L), any(), any()))
                .thenReturn(List.of(incident(80.0, d(2024, 6, 3))));   // 80 GBP -> 100 EUR
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(3L), any(), any()))
                .thenReturn(List.of());

        FinancialKpiDTO kpi = service.getFinancialKpi(3L, 2024, 6);

        assertThat(kpi.getCurrency()).isEqualTo("EUR");
        assertThat(kpi.getBudget()).isEqualTo(1000.0);
        assertThat(kpi.getIncidentRealCost()).isEqualTo(100.0);
        assertThat(kpi.getTotalRealCost()).isEqualTo(100.0);
        assertThat(kpi.getBudgetVariance()).isEqualTo(-900.0);
        assertThat(kpi.isFxComplete()).isTrue();
    }

    private static BudgetMonthly budgetGbp(double amount) {
        BudgetMonthly b = new BudgetMonthly();
        b.setAmount(amount);
        b.setCurrencyCode("GBP");
        return b;
    }

    private static tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate fx(int y, int m, String to, double rate) {
        tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate f =
                new tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate();
        f.setYear(y);
        f.setMonth(m);
        f.setFromCurrency("EUR");
        f.setToCurrency(to);
        f.setRate(rate);
        return f;
    }
}
