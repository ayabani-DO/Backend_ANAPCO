package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.EquipmentAnalyticsDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.EquipmentAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.EquipmentCostAnalysisDto;
import tn.esprit.examen.nomPrenomClasseExamen.cost.services.EquipmentCostAnalysisServiceImpl;
import tn.esprit.examen.nomPrenomClasseExamen.entities.*;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MonthlyFeatureAggregationService;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.*;
import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.EquipmentRulDto;
import tn.esprit.examen.nomPrenomClasseExamen.rul.services.EquipmentRulService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cross-service consistency of the canonical cost model:
 *  - EquipmentAnalyticsService and EquipmentCostAnalysisServiceImpl use the same realised cost;
 *  - FinancialAnalyticsService and MonthlyFeatureAggregationService use the same variance basis.
 */
@ExtendWith(MockitoExtension.class)
class CanonicalConsistencyTest {

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
    @Mock private EquipmentRulService rulService;

    private OperationalAnalyticsService operational;
    private FinancialAnalyticsService financial;
    private MonthlyFeatureAggregationService aggregation;
    private EquipmentAnalyticsService equipmentAnalytics;
    private EquipmentCostAnalysisServiceImpl equipmentCost;

    @BeforeEach
    void setUp() {
        CurrencyConverter cc = new CurrencyConverter(new FxRateService(fxRateRepository, null));
        operational = new OperationalAnalyticsService(incidentRepository, maintenanceRepository, sitesRepository, cc);
        financial = new FinancialAnalyticsService(sitesRepository, budgetMonthlyRepository, manualExpenseRepository, cc, operational);
        aggregation = new MonthlyFeatureAggregationService(
                sitesRepository, incidentRepository, maintenanceRepository, manualExpenseRepository,
                budgetMonthlyRepository, equipementRepository, oilPriceRecordRepository, energyPriceRecordRepository,
                riskAssessmentRepository, weatherAlertRepository, snapshotRepository, operational, cc);
        equipmentAnalytics = new EquipmentAnalyticsService(equipementRepository, operational, rulService);
        equipmentCost = new EquipmentCostAnalysisServiceImpl(equipementRepository, incidentRepository, maintenanceRepository, operational);
    }

    private static Date d(int y, int m, int day) {
        return Date.from(LocalDate.of(y, m, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Incident incident(double cost, Date date) {
        Incident i = new Incident();
        i.setCostReal(cost);
        i.setSeverityCode(SeverityCode.MEDIUM);
        i.setEtatIncident(EtatIncident.OPEN);
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
    void equipmentAnalyticsAndCostAnalysisAgreeOnRealisedCost() {
        Equipement pump = new Equipement();
        pump.setIdEquipement(1L);
        pump.setNomEquipement("Pump");

        List<Incident> incidents = List.of(incident(1000.0, d(2024, 6, 1)), incident(500.0, d(2024, 6, 4)));
        List<Maintenance> maintenances = List.of(
                maint(StatusMaintenace.DONE, 300.0, d(2024, 6, 2)),
                maint(StatusMaintenace.PLANNED, 900.0, d(2024, 7, 1)),
                maint(StatusMaintenace.IN_PROGRESS, 400.0, d(2024, 6, 20)));

        when(equipementRepository.findById(1L)).thenReturn(Optional.of(pump));
        when(incidentRepository.findByEquipementIdEquipement(1L)).thenReturn(incidents);
        when(maintenanceRepository.findByEquipementIdEquipement(1L)).thenReturn(maintenances);
        when(rulService.computeRul(1L)).thenReturn(EquipmentRulDto.builder().rulScore(20).estimatedRemainingDays(200).build());

        EquipmentAnalyticsDTO a = equipmentAnalytics.getEquipmentAnalytics(1L);
        EquipmentCostAnalysisDto c = equipmentCost.computeCostAnalysis(1L);

        // Realised operational cost = 1500 incident + 300 DONE maintenance = 1800 (planned/in-progress excluded).
        assertThat(a.getOperationalCost()).isEqualTo(1800.0);
        assertThat(c.getOperationalCost()).isEqualTo(1800.0);
        assertThat(a.getOperationalCost()).isEqualTo(c.getOperationalCost());
        assertThat(a.getTotalCost()).isEqualTo(c.getTotalCost());
        assertThat(a.getRealisedMaintenanceCost()).isEqualTo(c.getTotalMaintenanceCost());
    }

    @Test
    void financialAndMlSnapshotUseTheSameVarianceBasis() {
        Sites site = new Sites();
        site.setIdSite(1L);
        site.setCurrencyCode("EUR");
        site.setCountryCode("DE");
        lenient().when(sitesRepository.findById(1L)).thenReturn(Optional.of(site));

        List<Incident> incidents = List.of(incident(100.0, d(2024, 6, 3)));
        List<Maintenance> maintenances = List.of(
                maint(StatusMaintenace.DONE, 50.0, d(2024, 6, 5)),
                maint(StatusMaintenace.PLANNED, 80.0, d(2024, 6, 9)));   // planned excluded from both
        ManualExpense e = new ManualExpense();
        e.setAmount(25.0);
        e.setCurrencyCode("EUR");
        e.setDate(LocalDate.of(2024, 6, 12));

        lenient().when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(incidents);
        lenient().when(maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(maintenances);
        lenient().when(manualExpenseRepository.findBySite_IdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(List.of(e));
        lenient().when(budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(eq(1L), eq(2024), eq(6)))
                .thenReturn(Optional.of(budget(1000.0)));
        lenient().when(equipementRepository.findBySiteIdSite(1L)).thenReturn(List.of());
        lenient().when(snapshotRepository.findBySiteIdSiteAndYearAndMonth(eq(1L), eq(2024), eq(6))).thenReturn(Optional.empty());
        lenient().when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(snapshotRepository.findAllByOrderByYearDescMonthDesc()).thenReturn(List.of());
        lenient().when(oilPriceRecordRepository.findAvgPriceBetween(any(), any())).thenReturn(Optional.empty());
        lenient().when(energyPriceRecordRepository.findAvgGasPriceBetween(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(energyPriceRecordRepository.findAvgElectricityPriceBetween(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(riskAssessmentRepository.findBySiteIdSiteAndAssessedAtBetween(eq(1L), any(), any())).thenReturn(List.of());
        lenient().when(weatherAlertRepository.findBySiteIdSiteAndCreatedAtBetween(eq(1L), any(), any())).thenReturn(List.of());

        FinancialKpiDTO fin = financial.getFinancialKpi(1L, 2024, 6);
        MonthlyFeatureSnapshot snap = aggregation.computeForSite(site, 2024, 6);

        // totalRealCost = 100 + 50 + 25 = 175 ; variance = 175 - 1000 = -825 ; -82.5 %
        assertThat(fin.getTotalRealCost()).isEqualTo(175.0);
        assertThat(snap.getTotalCostEur()).isEqualTo(175.0);
        assertThat(fin.getBudgetVariancePercent()).isEqualTo(-82.5);
        assertThat(snap.getBudgetVariancePct()).isEqualTo(-82.5);
        assertThat(fin.getBudgetVariancePercent()).isEqualTo(snap.getBudgetVariancePct());
    }

    private static BudgetMonthly budget(double amount) {
        BudgetMonthly b = new BudgetMonthly();
        b.setAmount(amount);
        b.setCurrencyCode("EUR");
        return b;
    }
}
