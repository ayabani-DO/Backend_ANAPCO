package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.EquipmentCostAnalysisDto;
import tn.esprit.examen.nomPrenomClasseExamen.cost.services.EquipmentCostAnalysisServiceImpl;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Parity lock for {@link EquipmentCostAnalysisServiceImpl#computeCostAnalysis}. These values must
 * stay identical after the cost/count primitives are delegated to {@link OperationalAnalyticsService}.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentCostAnalysisParityTest {

    @Mock
    private EquipementRepository equipementRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MaintenanceRepository maintenanceRepository;

    private EquipmentCostAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        OperationalAnalyticsService operational =
                new OperationalAnalyticsService(incidentRepository, maintenanceRepository);
        service = new EquipmentCostAnalysisServiceImpl(
                equipementRepository, incidentRepository, maintenanceRepository, operational);
    }

    private static Incident incident(Double cost) {
        Incident i = new Incident();
        i.setCostReal(cost);
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
    void costAnalysisValuesAreStable() {
        Equipement pump = new Equipement();
        pump.setIdEquipement(1L);
        pump.setNomEquipement("Pump");

        when(equipementRepository.findById(1L)).thenReturn(Optional.of(pump));
        when(incidentRepository.findByEquipementIdEquipement(1L))
                .thenReturn(List.of(incident(1000.0), incident(500.0)));
        when(maintenanceRepository.findByEquipementIdEquipement(1L)).thenReturn(List.of(
                maint(TypeMaintenance.PREVENTIVE, StatusMaintenace.DONE, 200.0),
                maint(TypeMaintenance.CORRECTIVE, StatusMaintenace.DONE, 300.0),
                maint(TypeMaintenance.PREVENTIVE, StatusMaintenace.PLANNED, 400.0)
        ));

        EquipmentCostAnalysisDto dto = service.computeCostAnalysis(1L);

        assertThat(dto.getTotalIncidentCost()).isEqualTo(1500.0);
        assertThat(dto.getTotalMaintenanceCost()).isEqualTo(500.0);   // DONE only: 200 + 300
        assertThat(dto.getTotalCost()).isEqualTo(2000.0);
        assertThat(dto.getPlannedMaintenanceCost()).isEqualTo(400.0);
        assertThat(dto.getForecastTotalCost()).isEqualTo(2400.0);     // 2000 + 400
        assertThat(dto.getPreventiveCount()).isEqualTo(2);            // DONE + PLANNED
        assertThat(dto.getCorrectiveCount()).isEqualTo(1);
        assertThat(dto.getInspectionCount()).isZero();
    }
}
