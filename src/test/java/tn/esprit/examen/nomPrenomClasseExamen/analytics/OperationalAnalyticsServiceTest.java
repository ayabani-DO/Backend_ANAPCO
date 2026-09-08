package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
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
 * Deterministic, DB-free verification of the canonical operational formulas.
 * Site currency is EUR (= reporting currency) so conversions are identity and no FxRate is touched.
 */
@ExtendWith(MockitoExtension.class)
class OperationalAnalyticsServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MaintenanceRepository maintenanceRepository;
    @Mock
    private SitesRepository sitesRepository;
    @Mock
    private FxRateRepository fxRateRepository;

    private OperationalAnalyticsService service;

    @BeforeEach
    void setUp() {
        CurrencyConverter converter = new CurrencyConverter(new FxRateService(fxRateRepository, null));
        service = new OperationalAnalyticsService(incidentRepository, maintenanceRepository, sitesRepository, converter);
    }

    private Sites siteEur(long id) {
        Sites s = new Sites();
        s.setIdSite(id);
        s.setCurrencyCode("EUR");
        lenient().when(sitesRepository.findById(id)).thenReturn(Optional.of(s));
        return s;
    }

    private static Date d(int y, int m, int day) {
        return Date.from(LocalDate.of(y, m, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Equipement equip(long id, String name) {
        Equipement e = new Equipement();
        e.setIdEquipement(id);
        e.setNomEquipement(name);
        return e;
    }

    private static Incident incident(SeverityCode sev, Double cost, EtatIncident state,
                                     Date date, Date closed, Equipement eq) {
        Incident i = new Incident();
        i.setSeverityCode(sev);
        i.setCostReal(cost);
        i.setEtatIncident(state);
        i.setDate(date);
        i.setClosedDate(closed);
        i.setEquipement(eq);
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
    void computesConsolidatedOperationalKpi() {
        siteEur(1L);
        Equipement pump = equip(1L, "Pump");
        Equipement valve = equip(2L, "Valve");

        List<Incident> incidents = List.of(
                incident(SeverityCode.CRITICAL, 1000.0, EtatIncident.CLOSED, d(2024, 6, 1), d(2024, 6, 3), pump),
                incident(SeverityCode.HIGH, 500.0, EtatIncident.OPEN, d(2024, 6, 5), null, pump),
                incident(SeverityCode.LOW, null, EtatIncident.CLOSED, d(2024, 6, 10), d(2024, 6, 12), valve)
        );
        List<Maintenance> maintenances = List.of(
                maint(TypeMaintenance.PREVENTIVE, StatusMaintenace.DONE, 200.0),
                maint(TypeMaintenance.CORRECTIVE, StatusMaintenace.DONE, 300.0),
                maint(TypeMaintenance.CORRECTIVE, StatusMaintenace.PLANNED, 999.0),   // planned, not realised
                maint(TypeMaintenance.INSPECTION, StatusMaintenace.DONE, null)        // counted, no cost
        );

        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(incidents);
        when(maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(eq(1L), any(), any())).thenReturn(maintenances);

        OperationalKpiDTO kpi = service.getOperationalKpi(1L, 2024, 6);

        assertThat(kpi.getIncidentCount()).isEqualTo(3);
        assertThat(kpi.getCriticalIncidentCount()).isEqualTo(1);
        assertThat(kpi.getPreventiveCount()).isEqualTo(1);
        assertThat(kpi.getCorrectiveCount()).isEqualTo(2);
        assertThat(kpi.getInspectionCount()).isEqualTo(1);

        // canonical cost fields
        assertThat(kpi.getCurrency()).isEqualTo("EUR");
        assertThat(kpi.getIncidentRealCost()).isEqualTo(1500.0);        // 1000 + 500 (null excluded)
        assertThat(kpi.getRealisedMaintenanceCost()).isEqualTo(500.0);  // 200 + 300 (DONE only)
        assertThat(kpi.getPlannedMaintenanceCost()).isEqualTo(999.0);   // PLANNED
        assertThat(kpi.getOperationalCost()).isEqualTo(2000.0);         // 1500 + 500 (planned excluded)
        assertThat(kpi.isFxComplete()).isTrue();
        assertThat(kpi.getFxUnavailable()).isEmpty();

        // legacy aliases still populated
        assertThat(kpi.getIncidentCost()).isEqualTo(1500.0);
        assertThat(kpi.getMaintenanceCost()).isEqualTo(500.0);
        assertThat(kpi.getTotalOperationalCost()).isEqualTo(2000.0);

        assertThat(kpi.getSeverityIndex()).isEqualTo(2.67);         // (4+3+1)/3
        assertThat(kpi.getRecurrenceRate()).isEqualTo(33.33);       // Pump repeated -> 1/3
        assertThat(kpi.getAverageMTTR()).isEqualTo(2.0);
        assertThat(kpi.getAverageMTBF()).isEqualTo(10.0);           // 30 days / 3 incidents
        assertThat(kpi.getAvailability()).isEqualTo(83.33);
        assertThat(kpi.getTopRecurringEquipments()).containsExactly("Pump");
    }

    @Test
    void emptyDataYieldsZeroesAndFullAvailability() {
        siteEur(9L);
        when(incidentRepository.findBySitesIdSiteAndDateBetween(eq(9L), any(), any())).thenReturn(List.of());
        when(maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(eq(9L), any(), any())).thenReturn(List.of());

        OperationalKpiDTO kpi = service.getOperationalKpi(9L, 2024, 2);

        assertThat(kpi.getIncidentCount()).isZero();
        assertThat(kpi.getOperationalCost()).isZero();
        assertThat(kpi.getTotalOperationalCost()).isZero();
        assertThat(kpi.isFxComplete()).isTrue();
        assertThat(kpi.getSeverityIndex()).isZero();
        assertThat(kpi.getAverageMTBF()).isEqualTo(29.0);   // Feb 2024 has 29 days, no incidents
        assertThat(kpi.getAvailability()).isEqualTo(100.0);
        assertThat(kpi.getTopRecurringEquipments()).isEmpty();
    }
}
