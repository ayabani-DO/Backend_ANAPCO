package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.DependencyExistsException;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the STEP 2A cross-site guard in the existing incident-attachment
 * logic ({@link IncidentService#affecterIncidentToSite}). No incident CRUD is implemented here.
 */
@ExtendWith(MockitoExtension.class)
class IncidentAttachmentGuardTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private SitesRepository sitesRepository;
    @Mock private EquipementRepository equipementRepository;
    @InjectMocks private IncidentService incidentService;

    private static Sites site(Long id) {
        Sites s = new Sites();
        s.setIdSite(id);
        return s;
    }

    private static Incident incidentOnEquipment(Sites equipmentSite) {
        Incident i = new Incident();
        Equipement e = new Equipement();
        e.setIdEquipement(50L);
        e.setSite(equipmentSite);
        i.setEquipement(e);
        return i;
    }

    @Test
    void attach_missingIncident_isNotFound() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> incidentService.affecterIncidentToSite(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void attach_missingSite_isNotFound() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(new Incident()));
        when(sitesRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> incidentService.affecterIncidentToSite(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void attach_incidentEquipmentOnDifferentSite_isRejected() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incidentOnEquipment(site(99L))));
        when(sitesRepository.findById(2L)).thenReturn(Optional.of(site(2L)));

        assertThatThrownBy(() -> incidentService.affecterIncidentToSite(1L, 2L))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("different");
    }

    @Test
    void attach_incidentEquipmentWithNullSite_isRejected() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incidentOnEquipment(null)));
        when(sitesRepository.findById(2L)).thenReturn(Optional.of(site(2L)));

        assertThatThrownBy(() -> incidentService.affecterIncidentToSite(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no site");
    }

    @Test
    void attach_incidentWithoutEquipment_succeeds() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(new Incident()));
        when(sitesRepository.findById(2L)).thenReturn(Optional.of(site(2L)));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> incidentService.affecterIncidentToSite(1L, 2L)).doesNotThrowAnyException();
    }

    @Test
    void attach_incidentEquipmentOnSameSite_succeeds() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incidentOnEquipment(site(2L))));
        when(sitesRepository.findById(2L)).thenReturn(Optional.of(site(2L)));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> incidentService.affecterIncidentToSite(1L, 2L)).doesNotThrowAnyException();
    }
}
