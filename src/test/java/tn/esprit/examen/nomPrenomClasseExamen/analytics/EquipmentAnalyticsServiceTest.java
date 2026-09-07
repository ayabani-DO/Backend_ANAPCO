package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.EquipmentAnalyticsDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.EquipmentAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.EquipmentRulDto;
import tn.esprit.examen.nomPrenomClasseExamen.rul.services.EquipmentRulService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies that the equipment analytics view is correctly assembled from the operational primitives
 * (real {@link OperationalAnalyticsService}) and the RUL engine (mocked), with no recalculation.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentAnalyticsServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MaintenanceRepository maintenanceRepository;
    @Mock
    private EquipementRepository equipementRepository;
    @Mock
    private EquipmentRulService rulService;

    private EquipmentAnalyticsService service;

    @BeforeEach
    void setUp() {
        OperationalAnalyticsService operational =
                new OperationalAnalyticsService(incidentRepository, maintenanceRepository);
        service = new EquipmentAnalyticsService(equipementRepository, operational, rulService);
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

    private static Maintenance maint(TypeMaintenance type, StatusMaintenace status, Double cost, Date date) {
        Maintenance m = new Maintenance();
        m.setTypeMaintenance(type);
        m.setStatusMaintenance(status);
        m.setCostReal(cost);
        m.setDate(date);
        return m;
    }

    @Test
    void assemblesEquipmentHealthCardFromPrimitivesAndRul() {
        Sites site = new Sites();
        site.setNom("Plant-A");
        Equipement pump = new Equipement();
        pump.setIdEquipement(1L);
        pump.setNomEquipement("Pump");
        pump.setSite(site);

        List<Incident> incidents = List.of(
                incident(SeverityCode.CRITICAL, 1000.0, EtatIncident.CLOSED, d(2024, 6, 1), d(2024, 6, 3)),
                incident(SeverityCode.HIGH, 500.0, EtatIncident.OPEN, d(2024, 6, 5), null)
        );
        List<Maintenance> maintenances = List.of(
                maint(TypeMaintenance.PREVENTIVE, StatusMaintenace.DONE, 200.0, d(2024, 5, 1)),
                maint(TypeMaintenance.CORRECTIVE, StatusMaintenace.DONE, 300.0, d(2024, 5, 10)),
                maint(TypeMaintenance.PREVENTIVE, StatusMaintenace.PLANNED, 400.0, d(2024, 7, 1))
        );
        EquipmentRulDto rul = EquipmentRulDto.builder()
                .rulScore(40)
                .estimatedRemainingDays(120)
                .recommendedAction("Monitor closely")
                .build();

        when(equipementRepository.findById(1L)).thenReturn(Optional.of(pump));
        when(incidentRepository.findByEquipementIdEquipement(1L)).thenReturn(incidents);
        when(maintenanceRepository.findByEquipementIdEquipement(1L)).thenReturn(maintenances);
        when(rulService.computeRul(1L)).thenReturn(rul);

        EquipmentAnalyticsDTO dto = service.getEquipmentAnalytics(1L);

        assertThat(dto.getEquipmentName()).isEqualTo("Pump");
        assertThat(dto.getSiteName()).isEqualTo("Plant-A");
        assertThat(dto.getTotalCost()).isEqualTo(2400.0);   // 1500 incident + 500 realised + 400 planned
        assertThat(dto.getForecastCost()).isEqualTo(400.0); // planned only
        assertThat(dto.getRulScore()).isEqualTo(40);
        assertThat(dto.getRemainingDays()).isEqualTo(120);
        assertThat(dto.getRulAction()).isEqualTo("Monitor closely");
        assertThat(dto.getHealthScore()).isEqualTo(60.0);   // 100 - 40
        assertThat(dto.getRiskLevel()).isEqualTo("MEDIUM"); // 30 <= 40 < 60

        assertThat(dto.getIncidentSummary().count()).isEqualTo(2);
        assertThat(dto.getIncidentSummary().lastDate()).isEqualTo(LocalDate.of(2024, 6, 5));
        assertThat(dto.getIncidentSummary().avgSeverity()).isEqualTo(3.5); // (4 + 3) / 2

        assertThat(dto.getMaintenanceSummary().preventive()).isEqualTo(2);
        assertThat(dto.getMaintenanceSummary().corrective()).isEqualTo(1);
        assertThat(dto.getMaintenanceSummary().inspection()).isZero();
        assertThat(dto.getMaintenanceSummary().nextPlanned()).isEqualTo(LocalDate.of(2024, 7, 1));
    }
}
