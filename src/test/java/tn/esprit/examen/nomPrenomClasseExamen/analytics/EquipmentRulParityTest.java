package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.EquipmentRulDto;
import tn.esprit.examen.nomPrenomClasseExamen.rul.services.EquipmentRulServiceImpl;

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
 * Parity lock for the RUL engine after MTBF/MTTR are delegated to {@link OperationalAnalyticsService}.
 * The reliability figures (and the 90-day window semantics) must be identical to the legacy code.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentRulParityTest {

    @Mock
    private EquipementRepository equipementRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MaintenanceRepository maintenanceRepository;

    private EquipmentRulServiceImpl service;

    @BeforeEach
    void setUp() {
        OperationalAnalyticsService operational =
                new OperationalAnalyticsService(incidentRepository, maintenanceRepository);
        service = new EquipmentRulServiceImpl(
                equipementRepository, incidentRepository, maintenanceRepository, operational);
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

    @Test
    void mtbfAndMttrAreStableAfterDelegation() {
        Equipement pump = new Equipement();
        pump.setIdEquipement(1L);
        pump.setNomEquipement("Pump");

        List<Incident> recentIncidents = List.of(
                incident(SeverityCode.CRITICAL, 1000.0, EtatIncident.CLOSED, d(2024, 6, 1), d(2024, 6, 3)),
                incident(SeverityCode.HIGH, 500.0, EtatIncident.OPEN, d(2024, 6, 5), null)
        );
        Maintenance corrective = new Maintenance();
        corrective.setTypeMaintenance(TypeMaintenance.CORRECTIVE);
        corrective.setStatusMaintenance(StatusMaintenace.DONE);

        when(equipementRepository.findById(1L)).thenReturn(Optional.of(pump));
        when(incidentRepository.findByEquipementIdEquipementAndDateBetween(eq(1L), any(), any()))
                .thenReturn(recentIncidents);
        when(maintenanceRepository.findByEquipementIdEquipementAndTypeMaintenanceAndDateBetween(
                eq(1L), eq(TypeMaintenance.CORRECTIVE), any(), any())).thenReturn(List.of(corrective));
        when(maintenanceRepository.findByEquipementIdEquipement(1L)).thenReturn(List.of(corrective));

        EquipmentRulDto dto = service.computeRul(1L);

        assertThat(dto.getMtbf()).isEqualTo(45.0);   // 90-day window / 2 incidents
        assertThat(dto.getMttr()).isEqualTo(2.0);    // one closed incident, 2 days
        assertThat(dto.getRecentIncidentCount()).isEqualTo(2);
        assertThat(dto.getRecentCorrectiveMaintenanceCount()).isEqualTo(1);
    }
}
