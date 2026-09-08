package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.MonthlyFeatureSnapshot;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MonthlyFeatureAggregationService;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EnergyPriceRecordRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MonthlyFeatureSnapshotRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.OilPriceRecordRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;
import tn.esprit.examen.nomPrenomClasseExamen.services.FxRateService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.RiskAssessmentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.WeatherAlertRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Parity lock for {@link MonthlyFeatureAggregationService#computeForSite} — the ML feature source.
 * These snapshot values must be identical after incident/maintenance data + counts are routed through
 * the Analytics layer and FX conversion through the shared {@link CurrencyConverter}.
 */
@ExtendWith(MockitoExtension.class)
class MonthlyFeatureAggregationParityTest {

    @Mock private SitesRepository sitesRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private ManualExpenseRepository manualExpenseRepository;
    @Mock private BudgetMonthlyRepository budgetMonthlyRepository;
    @Mock private EquipementRepository equipementRepository;
    @Mock private OilPriceRecordRepository oilPriceRecordRepository;
    @Mock private EnergyPriceRecordRepository energyPriceRecordRepository;
    @Mock private RiskAssessmentRepository riskAssessmentRepository;
    @Mock private WeatherAlertRepository weatherAlertRepository;
    @Mock private MonthlyFeatureSnapshotRepository snapshotRepository;
    @Mock private FxRateRepository fxRateRepository;

    private MonthlyFeatureAggregationService service;

    @BeforeEach
    void setUp() {
        CurrencyConverter converter = new CurrencyConverter(new FxRateService(fxRateRepository, null));
        OperationalAnalyticsService operational =
                new OperationalAnalyticsService(incidentRepository, maintenanceRepository, sitesRepository, converter);
        service = new MonthlyFeatureAggregationService(
                sitesRepository, incidentRepository, maintenanceRepository, manualExpenseRepository,
                budgetMonthlyRepository, equipementRepository, oilPriceRecordRepository,
                energyPriceRecordRepository, riskAssessmentRepository, weatherAlertRepository,
                snapshotRepository, operational, converter);
    }

    private static Date d(int y, int m, int day) {
        return Date.from(LocalDate.of(y, m, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Incident incident(SeverityCode sev, Double cost, EtatIncident state, Date date, Date closed) {
        Incident i = new Incident();
        i.setSeverityCode(sev);
        i.setCostReal(cost);
        i.setEtatIncident(state);
        i.setDate(date);
        i.setClosedDate(closed);
        return i;
    }

    private static Maintenance maint(TypeMaintenance type, StatusMaintenace status, Double cost) {
        Maintenance m = new Maintenance();
        m.setTypeMaintenance(type);
        m.setStatusMaintenance(status);
        m.setCostReal(cost);
        return m;
    }

    @Test
    void snapshotFeatureValuesAreStable() {
        Sites site = new Sites();
        site.setIdSite(1L);
        site.setCurrencyCode("EUR");
        site.setCountryCode("DE");

        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(List.of(
                incident(SeverityCode.CRITICAL, 1000.0, EtatIncident.CLOSED, d(2024, 6, 1), d(2024, 6, 3)),
                incident(SeverityCode.HIGH, 500.0, EtatIncident.OPEN, d(2024, 6, 5), null)
        ));
        when(maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(List.of(
                maint(TypeMaintenance.PREVENTIVE, StatusMaintenace.DONE, 200.0),
                maint(TypeMaintenance.CORRECTIVE, StatusMaintenace.PLANNED, 300.0)
        ));
        ManualExpense e = new ManualExpense();
        e.setAmount(100.0);
        e.setCurrencyCode("EUR");
        e.setDate(LocalDate.of(2024, 6, 15));
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(List.of(e));

        BudgetMonthly budget = new BudgetMonthly();
        budget.setAmount(1000.0);
        budget.setCurrencyCode("EUR");
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(1L, 2024, 6)).thenReturn(Optional.of(budget));

        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MonthlyFeatureSnapshot snap = service.computeForSite(site, 2024, 6);

        assertThat(snap.getIncidentCount()).isEqualTo(2);
        assertThat(snap.getCriticalIncidentCount()).isEqualTo(1);
        assertThat(snap.getHighIncidentCount()).isEqualTo(1);
        assertThat(snap.getAvgIncidentSeverity()).isEqualTo(3.5); // (4 + 3) / 2
        assertThat(snap.getIncidentCostEur()).isEqualTo(1500.0);
        assertThat(snap.getPreventiveMaintenanceCount()).isEqualTo(1);
        assertThat(snap.getCorrectiveMaintenanceCount()).isEqualTo(1);
        assertThat(snap.getInspectionCount()).isZero();
        assertThat(snap.getMaintenanceCostEur()).isEqualTo(200.0); // realised (DONE) only: 200
        assertThat(snap.getManualExpenseEur()).isEqualTo(100.0);
        assertThat(snap.getBudgetEur()).isEqualTo(1000.0);
        assertThat(snap.getTotalCostEur()).isEqualTo(1800.0);      // 1500 + 200 + 100
        assertThat(snap.getBudgetVariancePct()).isEqualTo(80.0);   // (1800 - 1000) / 1000 * 100
        assertThat(snap.getEquipmentCount()).isZero();
        assertThat(snap.getRiskClass()).isEqualTo("HIGH_RISK");
    }

    @Test
    void snapshotIsNotPersistedWhenFxConversionIsIncomplete() {
        Sites site = new Sites();
        site.setIdSite(2L);
        site.setCurrencyCode("GBP");           // no EUR->GBP rate stubbed → conversion fails
        site.setCountryCode("GB");

        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(2L), any(), any())).thenReturn(List.of(
                incident(SeverityCode.CRITICAL, 1000.0, EtatIncident.OPEN, d(2024, 6, 1), null)));
        when(maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(eq(2L), any(), any())).thenReturn(List.of());
        when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(2L), any(), any())).thenReturn(List.of());
        when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(2L, 2024, 6)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.computeForSite(site, 2024, 6))
                .isInstanceOf(tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FxRateUnavailableException.class);

        org.mockito.Mockito.verify(snapshotRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
