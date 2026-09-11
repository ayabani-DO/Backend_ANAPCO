package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.CategorieEquipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.DependencyExistsException;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.CategorieEquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link EquipementService}:
 * <ul>
 *   <li>STEP 2A create/update relationship-integrity checks and the non-destructive delete guard;</li>
 *   <li>refEquipement backend generation ({@code <site.codeRef>-EQ-NNN}), client value ignored,
 *       immutable on update; serialNumber preserved as supplied and never fabricated.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EquipementServiceIntegrityTest {

    @Mock private EquipementRepository equipementRepo;
    @Mock private SitesRepository sitesRepo;
    @Mock private CategorieEquipementRepository categorieRepo;
    @Mock private IncidentRepository incidentRepo;
    @Mock private MaintenanceRepository maintenanceRepo;
    @InjectMocks private EquipementService equipementService;

    /** A bare id reference as a client would send it (no codeRef needed). */
    private static Sites siteRef(Long id) {
        Sites s = new Sites();
        s.setIdSite(id);
        return s;
    }

    /** A managed Site as it comes back from the repository, carrying its generated codeRef. */
    private static Sites managedSite(Long id, String codeRef) {
        Sites s = new Sites();
        s.setIdSite(id);
        s.setCodeRef(codeRef);
        return s;
    }

    private static CategorieEquipement categorieRef(Long id) {
        CategorieEquipement c = new CategorieEquipement();
        c.setIdCategorie(id);
        return c;
    }

    private static Equipement newEquipement(Sites site, CategorieEquipement categorie) {
        Equipement e = new Equipement();
        e.setNomEquipement("Pump A");
        e.setSite(site);
        e.setCategorie(categorie);
        return e;
    }

    private static Equipement existing(Long id, String refEquipement, String serialNumber) {
        Equipement e = new Equipement();
        e.setIdEquipement(id);
        e.setNomEquipement("Pump A");
        e.setRefEquipement(refEquipement);
        e.setSerialNumber(serialNumber);
        return e;
    }

    // ── STEP 2A relationship validation ──────────────────────────────────

    @Test
    void create_withoutSite_isRejected() {
        assertThatThrownBy(() -> equipementService.createEquipement(newEquipement(null, categorieRef(1L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site");
        verify(equipementRepo, never()).save(any());
    }

    @Test
    void create_withNonExistentSite_isNotFound() {
        when(sitesRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> equipementService.createEquipement(newEquipement(siteRef(99L), categorieRef(1L))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withoutCategory_isRejected() {
        when(sitesRepo.findById(1L)).thenReturn(Optional.of(managedSite(1L, "TN-PLANT-1")));
        assertThatThrownBy(() -> equipementService.createEquipement(newEquipement(siteRef(1L), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
    }

    @Test
    void create_withNonExistentCategory_isNotFound() {
        when(sitesRepo.findById(1L)).thenReturn(Optional.of(managedSite(1L, "TN-PLANT-1")));
        when(categorieRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> equipementService.createEquipement(newEquipement(siteRef(1L), categorieRef(99L))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withValidSiteAndCategory_isSaved() {
        when(sitesRepo.findById(1L)).thenReturn(Optional.of(managedSite(1L, "TN-PLANT-1")));
        when(categorieRepo.findById(2L)).thenReturn(Optional.of(categorieRef(2L)));
        when(equipementRepo.countBySiteIdSite(1L)).thenReturn(0L);
        when(equipementRepo.existsByRefEquipement("TN-PLANT-1-EQ-001")).thenReturn(false);
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> equipementService.createEquipement(newEquipement(siteRef(1L), categorieRef(2L))))
                .doesNotThrowAnyException();
        verify(equipementRepo).save(any(Equipement.class));
    }

    // ── refEquipement generation ────────────────────────────────────────

    @Test
    void create_nullRefEquipement_isGenerated_fromManagedSiteCodeRef_zeroPadded() {
        when(sitesRepo.findById(2L)).thenReturn(Optional.of(managedSite(2L, "TN-PLANT-2")));
        when(categorieRepo.findById(4L)).thenReturn(Optional.of(categorieRef(4L)));
        when(equipementRepo.countBySiteIdSite(2L)).thenReturn(0L);
        when(equipementRepo.existsByRefEquipement("TN-PLANT-2-EQ-001")).thenReturn(false);
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement in = newEquipement(siteRef(2L), categorieRef(4L));
        in.setRefEquipement(null);

        ArgumentCaptor<Equipement> captor = ArgumentCaptor.forClass(Equipement.class);
        equipementService.createEquipement(in);
        verify(equipementRepo).save(captor.capture());

        assertThat(captor.getValue().getRefEquipement())
                .isEqualTo("TN-PLANT-2-EQ-001")
                .matches(".*-EQ-\\d{3}$");
    }

    @Test
    void create_clientSuppliedRefEquipement_isIgnored() {
        when(sitesRepo.findById(2L)).thenReturn(Optional.of(managedSite(2L, "TN-PLANT-2")));
        when(categorieRepo.findById(4L)).thenReturn(Optional.of(categorieRef(4L)));
        when(equipementRepo.countBySiteIdSite(2L)).thenReturn(0L);
        when(equipementRepo.existsByRefEquipement("TN-PLANT-2-EQ-001")).thenReturn(false);
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement in = newEquipement(siteRef(2L), categorieRef(4L));
        in.setRefEquipement("HACKED");

        Equipement saved = equipementService.createEquipement(in);

        assertThat(saved.getRefEquipement()).isEqualTo("TN-PLANT-2-EQ-001").isNotEqualTo("HACKED");
    }

    @Test
    void create_refEquipementCollision_incrementsToNextFreeSequence() {
        when(sitesRepo.findById(2L)).thenReturn(Optional.of(managedSite(2L, "TN-PLANT-2")));
        when(categorieRepo.findById(4L)).thenReturn(Optional.of(categorieRef(4L)));
        when(equipementRepo.countBySiteIdSite(2L)).thenReturn(0L);
        when(equipementRepo.existsByRefEquipement("TN-PLANT-2-EQ-001")).thenReturn(true);
        when(equipementRepo.existsByRefEquipement("TN-PLANT-2-EQ-002")).thenReturn(false);
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement saved = equipementService.createEquipement(newEquipement(siteRef(2L), categorieRef(4L)));

        assertThat(saved.getRefEquipement()).isEqualTo("TN-PLANT-2-EQ-002");
    }

    @Test
    void create_forAnotherSite_usesThatSitesCodeRefPrefix_andContinuesSequence() {
        when(sitesRepo.findById(7L)).thenReturn(Optional.of(managedSite(7L, "UK-PLANT-2")));
        when(categorieRepo.findById(4L)).thenReturn(Optional.of(categorieRef(4L)));
        when(equipementRepo.countBySiteIdSite(7L)).thenReturn(5L);
        when(equipementRepo.existsByRefEquipement("UK-PLANT-2-EQ-006")).thenReturn(false);
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement saved = equipementService.createEquipement(newEquipement(siteRef(7L), categorieRef(4L)));

        assertThat(saved.getRefEquipement()).isEqualTo("UK-PLANT-2-EQ-006");
    }

    // ── serialNumber: preserved, never fabricated ───────────────────────

    @Test
    void create_withSuppliedSerialNumber_isPreservedExactly() {
        when(sitesRepo.findById(2L)).thenReturn(Optional.of(managedSite(2L, "TN-PLANT-2")));
        when(categorieRepo.findById(4L)).thenReturn(Optional.of(categorieRef(4L)));
        when(equipementRepo.countBySiteIdSite(2L)).thenReturn(0L);
        when(equipementRepo.existsByRefEquipement("TN-PLANT-2-EQ-001")).thenReturn(false);
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement in = newEquipement(siteRef(2L), categorieRef(4L));
        in.setSerialNumber("SN-REAL-123");

        Equipement saved = equipementService.createEquipement(in);

        assertThat(saved.getSerialNumber()).isEqualTo("SN-REAL-123");
    }

    @Test
    void create_withNullSerialNumber_staysNull_noGeneratorInService() {
        when(sitesRepo.findById(2L)).thenReturn(Optional.of(managedSite(2L, "TN-PLANT-2")));
        when(categorieRepo.findById(4L)).thenReturn(Optional.of(categorieRef(4L)));
        when(equipementRepo.countBySiteIdSite(2L)).thenReturn(0L);
        when(equipementRepo.existsByRefEquipement("TN-PLANT-2-EQ-001")).thenReturn(false);
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement in = newEquipement(siteRef(2L), categorieRef(4L));
        in.setSerialNumber(null);

        Equipement saved = equipementService.createEquipement(in);

        assertThat(saved.getSerialNumber()).isNull();
    }

    // ── update: refEquipement immutable, serialNumber editable ──────────

    @Test
    void update_incomingRefEquipementDiffers_existingIsPreserved() {
        when(equipementRepo.findById(5L)).thenReturn(Optional.of(existing(5L, "TN-PLANT-2-EQ-001", "SN-1")));
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement incoming = new Equipement();
        incoming.setRefEquipement("CHANGED");
        incoming.setSerialNumber("SN-1");

        Equipement result = equipementService.updateEquipement(5L, incoming);

        assertThat(result.getRefEquipement()).isEqualTo("TN-PLANT-2-EQ-001");
    }

    @Test
    void update_incomingRefEquipementNull_existingIsPreserved() {
        when(equipementRepo.findById(5L)).thenReturn(Optional.of(existing(5L, "TN-PLANT-2-EQ-001", "SN-1")));
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement incoming = new Equipement();
        incoming.setRefEquipement(null);
        incoming.setSerialNumber("SN-1");

        Equipement result = equipementService.updateEquipement(5L, incoming);

        assertThat(result.getRefEquipement()).isEqualTo("TN-PLANT-2-EQ-001");
    }

    @Test
    void update_movingToAnotherSite_doesNotRegenerateRefEquipement() {
        when(equipementRepo.findById(5L)).thenReturn(Optional.of(existing(5L, "TN-PLANT-2-EQ-001", "SN-1")));
        when(sitesRepo.findById(9L)).thenReturn(Optional.of(managedSite(9L, "UK-PLANT-9")));
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement incoming = new Equipement();
        incoming.setSite(siteRef(9L));

        Equipement result = equipementService.updateEquipement(5L, incoming);

        assertThat(result.getRefEquipement()).isEqualTo("TN-PLANT-2-EQ-001");
        assertThat(result.getSite().getCodeRef()).isEqualTo("UK-PLANT-9");
    }

    @Test
    void update_canChangeSerialNumber() {
        when(equipementRepo.findById(5L)).thenReturn(Optional.of(existing(5L, "TN-PLANT-2-EQ-001", "SN-OLD")));
        when(equipementRepo.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));

        Equipement incoming = new Equipement();
        incoming.setSerialNumber("SN-NEW");

        Equipement result = equipementService.updateEquipement(5L, incoming);

        assertThat(result.getSerialNumber()).isEqualTo("SN-NEW");
    }

    // ── STEP 2A delete guard ────────────────────────────────────────────

    @Test
    void delete_missing_isNotFound() {
        when(equipementRepo.existsById(9L)).thenReturn(false);
        assertThatThrownBy(() -> equipementService.deleteEquipement(9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_withIncidentHistory_isConflict() {
        when(equipementRepo.existsById(1L)).thenReturn(true);
        when(incidentRepo.existsByEquipementIdEquipement(1L)).thenReturn(true);
        assertThatThrownBy(() -> equipementService.deleteEquipement(1L))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("incident");
        verify(equipementRepo, never()).deleteById(1L);
    }

    @Test
    void delete_withMaintenanceHistory_isConflict() {
        when(equipementRepo.existsById(1L)).thenReturn(true);
        when(incidentRepo.existsByEquipementIdEquipement(1L)).thenReturn(false);
        when(maintenanceRepo.existsByEquipementIdEquipement(1L)).thenReturn(true);
        assertThatThrownBy(() -> equipementService.deleteEquipement(1L))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("maintenance");
    }

    @Test
    void delete_withNoHistory_succeeds() {
        when(equipementRepo.existsById(1L)).thenReturn(true);
        when(incidentRepo.existsByEquipementIdEquipement(1L)).thenReturn(false);
        when(maintenanceRepo.existsByEquipementIdEquipement(1L)).thenReturn(false);

        assertThatCode(() -> equipementService.deleteEquipement(1L)).doesNotThrowAnyException();
        verify(equipementRepo).deleteById(1L);
    }
}
