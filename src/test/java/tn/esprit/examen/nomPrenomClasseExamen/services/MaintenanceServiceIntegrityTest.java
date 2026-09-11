package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the STEP 2A maintenance-integrity checks in {@link MaintenanceService}:
 * an existing equipment that itself belongs to a site, a mandatory date, and a non-negative costReal.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceServiceIntegrityTest {

    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private EquipementRepository equipementRepository;
    @InjectMocks private MaintenanceService maintenanceService;

    private static Equipement equipementWithSite(Long id, Long siteId) {
        Equipement e = new Equipement();
        e.setIdEquipement(id);
        if (siteId != null) {
            Sites s = new Sites();
            s.setIdSite(siteId);
            e.setSite(s);
        }
        return e;
    }

    private static Maintenance maintenance(Equipement equipement, Date date, Double costReal) {
        Maintenance m = new Maintenance();
        m.setEquipement(equipement);
        m.setDate(date);
        m.setCostReal(costReal);
        return m;
    }

    @Test
    void create_withoutEquipement_isRejected() {
        Maintenance m = maintenance(null, new Date(), 100.0);
        assertThatThrownBy(() -> maintenanceService.create(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equipment");
    }

    @Test
    void create_withNonExistentEquipement_isNotFound() {
        when(equipementRepository.findById(99L)).thenReturn(Optional.empty());
        Maintenance m = maintenance(equipementWithSite(99L, 1L), new Date(), 100.0);
        assertThatThrownBy(() -> maintenanceService.create(m))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withSitelessEquipement_isRejected() {
        when(equipementRepository.findById(5L)).thenReturn(Optional.of(equipementWithSite(5L, null)));
        Maintenance m = maintenance(equipementWithSite(5L, null), new Date(), 100.0);
        assertThatThrownBy(() -> maintenanceService.create(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no site");
    }

    @Test
    void create_withNegativeCostReal_isRejected() {
        Maintenance m = maintenance(equipementWithSite(5L, 1L), new Date(), -1.0);
        assertThatThrownBy(() -> maintenanceService.create(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("costReal");
    }

    @Test
    void create_withoutDate_isRejected() {
        Maintenance m = maintenance(equipementWithSite(5L, 1L), null, 100.0);
        assertThatThrownBy(() -> maintenanceService.create(m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void create_valid_isSaved() {
        when(equipementRepository.findById(5L)).thenReturn(Optional.of(equipementWithSite(5L, 1L)));
        when(maintenanceRepository.save(any(Maintenance.class))).thenAnswer(inv -> inv.getArgument(0));

        Maintenance m = maintenance(equipementWithSite(5L, 1L), new Date(), 0.0);
        assertThatCode(() -> maintenanceService.create(m)).doesNotThrowAnyException();
    }

    @Test
    void delete_missing_isNotFound() {
        when(maintenanceRepository.existsById(9L)).thenReturn(false);
        assertThatThrownBy(() -> maintenanceService.delete(9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
