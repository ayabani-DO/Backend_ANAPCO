package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.DependencyExistsException;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the STEP 2B Incident CRUD + domain-integrity guards in
 * {@link IncidentService}: Site is mandatory, Equipment is optional but must share the
 * Incident's Site, costs cannot be negative, severity stays optional, and CLOSED incidents
 * must carry a coherent closedDate.
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceCrudTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private SitesRepository sitesRepository;
    @Mock private EquipementRepository equipementRepository;
    @InjectMocks private IncidentService incidentService;

    private static Sites site(Long id) {
        Sites s = new Sites();
        s.setIdSite(id);
        s.setCodeRef("TN-PLANT-" + id);
        return s;
    }

    private static Equipement equipementRef(Long id) {
        Equipement e = new Equipement();
        e.setIdEquipement(id);
        return e;
    }

    private static Equipement equipementWithSite(Long id, Sites site) {
        Equipement e = new Equipement();
        e.setIdEquipement(id);
        e.setSite(site);
        return e;
    }

    private static Incident incident(Sites sites, Equipement equipement) {
        Incident i = new Incident();
        i.setDate(new Date());
        i.setEtatIncident(EtatIncident.OPEN);
        i.setSites(sites);
        i.setEquipement(equipement);
        return i;
    }

    // ── CREATE ────────────────────────────────────────────────────────────

    @Test
    void create_siteOnly_succeeds() {
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site(3L)));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident saved = incidentService.createIncident(incident(site(3L), null));

        assertThat(saved.getSites().getIdSite()).isEqualTo(3L);
        assertThat(saved.getEquipement()).isNull();
    }

    @Test
    void create_siteAndSameSiteEquipment_succeeds() {
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site(3L)));
        when(equipementRepository.findById(8L)).thenReturn(Optional.of(equipementWithSite(8L, site(3L))));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident saved = incidentService.createIncident(incident(site(3L), equipementRef(8L)));

        assertThat(saved.getSites().getIdSite()).isEqualTo(3L);
        assertThat(saved.getEquipement().getIdEquipement()).isEqualTo(8L);
    }

    @Test
    void create_missingSite_isRejected() {
        assertThatThrownBy(() -> incidentService.createIncident(incident(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site");
    }

    @Test
    void create_nonExistentSite_isNotFound() {
        when(sitesRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> incidentService.createIncident(incident(site(99L), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_nonExistentEquipment_isNotFound() {
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site(3L)));
        when(equipementRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> incidentService.createIncident(incident(site(3L), equipementRef(99L))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_equipmentWithNullSite_isRejected() {
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site(3L)));
        when(equipementRepository.findById(8L)).thenReturn(Optional.of(equipementWithSite(8L, null)));
        assertThatThrownBy(() -> incidentService.createIncident(incident(site(3L), equipementRef(8L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no site");
    }

    @Test
    void create_crossSiteEquipment_isConflict() {
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site(3L)));
        when(equipementRepository.findById(8L)).thenReturn(Optional.of(equipementWithSite(8L, site(9L))));
        assertThatThrownBy(() -> incidentService.createIncident(incident(site(3L), equipementRef(8L))))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("different");
    }

    @Test
    void create_missingDate_isRejected() {
        Incident payload = incident(site(3L), null);
        payload.setDate(null);
        assertThatThrownBy(() -> incidentService.createIncident(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    @Test
    void create_negativeCostEstimated_isRejected() {
        Incident payload = incident(site(3L), null);
        payload.setCostEstimated(-1.0);
        assertThatThrownBy(() -> incidentService.createIncident(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("costEstimated");
    }

    @Test
    void create_negativeCostReal_isRejected() {
        Incident payload = incident(site(3L), null);
        payload.setCostReal(-1.0);
        assertThatThrownBy(() -> incidentService.createIncident(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("costReal");
    }

    @Test
    void create_closedWithoutClosedDate_isRejected() {
        Incident payload = incident(site(3L), null);
        payload.setEtatIncident(EtatIncident.CLOSED);
        assertThatThrownBy(() -> incidentService.createIncident(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedDate");
    }

    @Test
    void create_closedDateBeforeDate_isRejected() {
        Incident payload = incident(site(3L), null);
        payload.setDate(new Date(2_000_000_000_000L));
        payload.setEtatIncident(EtatIncident.CLOSED);
        payload.setClosedDate(new Date(1_000_000_000_000L));
        assertThatThrownBy(() -> incidentService.createIncident(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedDate");
    }

    @Test
    void create_validClosed_succeeds() {
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site(3L)));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident payload = incident(site(3L), null);
        payload.setEtatIncident(EtatIncident.CLOSED);
        payload.setClosedDate(new Date(payload.getDate().getTime() + 86_400_000L));

        assertThatCode(() -> incidentService.createIncident(payload)).doesNotThrowAnyException();
    }

    @Test
    void create_nullSeverity_isAccepted() {
        when(sitesRepository.findById(3L)).thenReturn(Optional.of(site(3L)));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident payload = incident(site(3L), null);
        payload.setSeverityCode(null);

        Incident saved = incidentService.createIncident(payload);
        assertThat(saved.getSeverityCode()).isNull();
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    @Test
    void update_missingIncident_isNotFound() {
        when(incidentRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> incidentService.updateIncident(1L, incident(site(3L), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_valid_succeeds_andPreservesOmittedSite() {
        Incident existing = incident(site(3L), null);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident incoming = incident(null, null); // sites omitted -> existing site preserved
        incoming.setDescription("updated");

        Incident result = incidentService.updateIncident(5L, incoming);

        assertThat(result.getDescription()).isEqualTo("updated");
        assertThat(result.getSites().getIdSite()).isEqualTo(3L);
    }

    @Test
    void update_crossSiteEquipment_isConflict() {
        Incident existing = incident(site(3L), null);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(equipementRepository.findById(8L)).thenReturn(Optional.of(equipementWithSite(8L, site(9L))));

        Incident incoming = incident(null, equipementRef(8L));

        assertThatThrownBy(() -> incidentService.updateIncident(5L, incoming))
                .isInstanceOf(DependencyExistsException.class);
    }

    @Test
    void update_negativeCost_isRejected() {
        Incident existing = incident(site(3L), null);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(existing));

        Incident incoming = incident(null, null);
        incoming.setCostReal(-5.0);

        assertThatThrownBy(() -> incidentService.updateIncident(5L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("costReal");
    }

    @Test
    void update_invalidClosedState_isRejected() {
        Incident existing = incident(site(3L), null);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(existing));

        Incident incoming = incident(null, null);
        incoming.setEtatIncident(EtatIncident.CLOSED);
        incoming.setClosedDate(null);

        assertThatThrownBy(() -> incidentService.updateIncident(5L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedDate");
    }

    @Test
    void update_relationshipsResolvedServerSide_notTrustedFromPayload() {
        Incident existing = incident(site(3L), null);
        Sites managedSite = site(7L);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(sitesRepository.findById(7L)).thenReturn(Optional.of(managedSite));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        // The client can only ever send a bare id reference; the service must resolve the real
        // managed Site rather than trusting this nested object as a persistent entity.
        Sites unmanagedSiteStub = new Sites();
        unmanagedSiteStub.setIdSite(7L);

        Incident result = incidentService.updateIncident(5L, incident(unmanagedSiteStub, null));

        assertThat(result.getSites()).isSameAs(managedSite);
        verify(sitesRepository).findById(7L);
    }

    // ── DELETE ────────────────────────────────────────────────────────────

    @Test
    void delete_existing_succeeds() {
        when(incidentRepository.existsById(5L)).thenReturn(true);
        assertThatCode(() -> incidentService.deleteIncident(5L)).doesNotThrowAnyException();
        verify(incidentRepository).deleteById(5L);
    }

    @Test
    void delete_missing_isNotFound() {
        when(incidentRepository.existsById(9L)).thenReturn(false);
        assertThatThrownBy(() -> incidentService.deleteIncident(9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
