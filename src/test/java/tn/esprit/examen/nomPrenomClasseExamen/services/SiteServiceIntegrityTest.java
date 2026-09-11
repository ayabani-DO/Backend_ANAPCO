package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusSites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.DependencyExistsException;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link SiteService}:
 * <ul>
 *   <li>STEP 2A operational-integrity guards: minimum-information validation, coordinate ranges,
 *       and the non-destructive delete guard;</li>
 *   <li>codeRef correction: codeRef is backend-generated on create, any client value is ignored,
 *       collisions are skipped, and codeRef is immutable through update.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SiteServiceIntegrityTest {

    @Mock private SitesRepository siteRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private EquipementRepository equipementRepository;
    @Mock private BudgetMonthlyRepository budgetMonthlyRepository;
    @Mock private ManualExpenseRepository manualExpenseRepository;
    @InjectMocks private SiteService siteService;

    /** A payload as a well-behaved client would send it: no codeRef. */
    private static Sites validSite() {
        Sites s = new Sites();
        s.setNom("Aberdeen Plant");
        s.setCountryCode("TN");
        s.setCurrencyCode("GBP");
        s.setStatusSites(StatusSites.ACTIF);
        s.setLatitude(57.14);
        s.setLongitude(-2.09);
        return s;
    }

    // ── create: field validation ─────────────────────────────────────────

    @Test
    void createSite_blankName_isRejected() {
        Sites s = validSite();
        s.setNom(null);
        assertThatThrownBy(() -> siteService.createSite(s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nom");
    }

    @Test
    void createSite_missingCountryCode_isRejected() {
        Sites s = validSite();
        s.setCountryCode("  ");
        assertThatThrownBy(() -> siteService.createSite(s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("countryCode");
        verify(siteRepository, never()).save(any());
    }

    @Test
    void createSite_missingCurrency_isRejected_andNotDefaulted() {
        Sites s = validSite();
        s.setCurrencyCode(null);
        assertThatThrownBy(() -> siteService.createSite(s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencyCode");
        verify(siteRepository, never()).save(any());
    }

    @Test
    void createSite_nullStatus_isRejected() {
        Sites s = validSite();
        s.setStatusSites(null);
        assertThatThrownBy(() -> siteService.createSite(s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("statusSites");
    }

    @Test
    void createSite_latitudeOutOfRange_isRejected() {
        Sites s = validSite();
        s.setLatitude(91.0);
        assertThatThrownBy(() -> siteService.createSite(s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");
    }

    @Test
    void createSite_longitudeOutOfRange_isRejected() {
        Sites s = validSite();
        s.setLongitude(-180.5);
        assertThatThrownBy(() -> siteService.createSite(s))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitude");
    }

    // ── create: codeRef auto-generation ──────────────────────────────────

    @Test
    void createSite_nullCodeRef_isAccepted_andCodeRefIsGenerated() {
        when(siteRepository.countByCountryCode("TN")).thenReturn(0L);
        when(siteRepository.existsByCodeRef("TN-PLANT-1")).thenReturn(false);
        when(siteRepository.save(any(Sites.class))).thenAnswer(inv -> inv.getArgument(0));

        Sites saved = siteService.createSite(validSite());

        assertThat(saved.getCodeRef()).isNotBlank().isEqualTo("TN-PLANT-1");
        assertThat(saved.getCodeRef()).startsWith("TN-");
    }

    @Test
    void createSite_clientSuppliedCodeRef_isIgnored_andServerValueSaved() {
        Sites s = validSite();
        s.setCodeRef("HACKED-CODE");

        when(siteRepository.countByCountryCode("TN")).thenReturn(0L);
        when(siteRepository.existsByCodeRef("TN-PLANT-1")).thenReturn(false);
        when(siteRepository.save(any(Sites.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Sites> captor = ArgumentCaptor.forClass(Sites.class);
        siteService.createSite(s);
        verify(siteRepository).save(captor.capture());

        assertThat(captor.getValue().getCodeRef())
                .isEqualTo("TN-PLANT-1")
                .isNotEqualTo("HACKED-CODE");
    }

    @Test
    void createSite_codeRefCollision_incrementsToNextFreeReference() {
        when(siteRepository.countByCountryCode("TN")).thenReturn(0L);
        when(siteRepository.existsByCodeRef("TN-PLANT-1")).thenReturn(true);
        when(siteRepository.existsByCodeRef("TN-PLANT-2")).thenReturn(false);
        when(siteRepository.save(any(Sites.class))).thenAnswer(inv -> inv.getArgument(0));

        Sites saved = siteService.createSite(validSite());

        assertThat(saved.getCodeRef()).isEqualTo("TN-PLANT-2");
    }

    @Test
    void createSite_countryCodeIsTrimmedAndUpperCasedInReference() {
        Sites s = validSite();
        s.setCountryCode(" uk ");

        when(siteRepository.countByCountryCode("UK")).thenReturn(2L);
        when(siteRepository.existsByCodeRef("UK-PLANT-3")).thenReturn(false);
        when(siteRepository.save(any(Sites.class))).thenAnswer(inv -> inv.getArgument(0));

        Sites saved = siteService.createSite(s);

        assertThat(saved.getCodeRef()).isEqualTo("UK-PLANT-3");
    }

    // ── delete guard (STEP 2A) ───────────────────────────────────────────

    @Test
    void deleteSite_missing_isNotFound() {
        when(siteRepository.existsById(9L)).thenReturn(false);
        assertThatThrownBy(() -> siteService.deleteSite(9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSite_withEquipment_isConflict() {
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(equipementRepository.existsBySiteIdSite(1L)).thenReturn(true);
        assertThatThrownBy(() -> siteService.deleteSite(1L))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("equipment");
        verify(siteRepository, never()).deleteById(1L);
    }

    @Test
    void deleteSite_withIncident_isConflict() {
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(equipementRepository.existsBySiteIdSite(1L)).thenReturn(false);
        when(incidentRepository.existsBySitesIdSite(1L)).thenReturn(true);
        assertThatThrownBy(() -> siteService.deleteSite(1L))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("incident");
    }

    @Test
    void deleteSite_withBudget_isConflict() {
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(equipementRepository.existsBySiteIdSite(1L)).thenReturn(false);
        when(incidentRepository.existsBySitesIdSite(1L)).thenReturn(false);
        when(budgetMonthlyRepository.existsBySite_IdSite(1L)).thenReturn(true);
        assertThatThrownBy(() -> siteService.deleteSite(1L))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("budget");
    }

    @Test
    void deleteSite_withManualExpense_isConflict() {
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(equipementRepository.existsBySiteIdSite(1L)).thenReturn(false);
        when(incidentRepository.existsBySitesIdSite(1L)).thenReturn(false);
        when(budgetMonthlyRepository.existsBySite_IdSite(1L)).thenReturn(false);
        when(manualExpenseRepository.existsBySite_IdSite(1L)).thenReturn(true);
        assertThatThrownBy(() -> siteService.deleteSite(1L))
                .isInstanceOf(DependencyExistsException.class)
                .hasMessageContaining("manual expenses");
    }

    @Test
    void deleteSite_withNoDependencies_succeeds() {
        when(siteRepository.existsById(1L)).thenReturn(true);
        when(equipementRepository.existsBySiteIdSite(1L)).thenReturn(false);
        when(incidentRepository.existsBySitesIdSite(1L)).thenReturn(false);
        when(budgetMonthlyRepository.existsBySite_IdSite(1L)).thenReturn(false);
        when(manualExpenseRepository.existsBySite_IdSite(1L)).thenReturn(false);

        assertThatCode(() -> siteService.deleteSite(1L)).doesNotThrowAnyException();
        verify(siteRepository).deleteById(1L);
    }

    // ── update: codeRef immutability + STEP 2A validation ────────────────

    @Test
    void updateSite_missing_isNotFound() {
        when(siteRepository.findById(7L)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> siteService.updateSite(7L, validSite()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSite_incomingCodeRefDiffers_existingCodeRefIsPreserved() {
        Sites existing = validSite();
        existing.setCodeRef("TN-PLANT-7");
        when(siteRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
        when(siteRepository.save(any(Sites.class))).thenAnswer(inv -> inv.getArgument(0));

        Sites incoming = validSite();
        incoming.setCodeRef("CHANGED");

        Sites result = siteService.updateSite(5L, incoming);

        assertThat(result.getCodeRef()).isEqualTo("TN-PLANT-7");
    }

    @Test
    void updateSite_incomingCodeRefNull_existingCodeRefIsPreserved() {
        Sites existing = validSite();
        existing.setCodeRef("TN-PLANT-7");
        when(siteRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
        when(siteRepository.save(any(Sites.class))).thenAnswer(inv -> inv.getArgument(0));

        Sites incoming = validSite();
        incoming.setCodeRef(null);

        Sites result = siteService.updateSite(5L, incoming);

        assertThat(result.getCodeRef()).isEqualTo("TN-PLANT-7");
    }

    @Test
    void updateSite_stillRejectsBlankCurrency() {
        Sites existing = validSite();
        existing.setCodeRef("TN-PLANT-7");
        when(siteRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));

        Sites incoming = validSite();
        incoming.setCurrencyCode("  ");

        assertThatThrownBy(() -> siteService.updateSite(5L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencyCode");
        verify(siteRepository, never()).save(any());
    }

    @Test
    void updateSite_stillRejectsInvalidLatitude() {
        Sites existing = validSite();
        existing.setCodeRef("TN-PLANT-7");
        when(siteRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));

        Sites incoming = validSite();
        incoming.setLatitude(120.0);

        assertThatThrownBy(() -> siteService.updateSite(5L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitude");
        verify(siteRepository, never()).save(any());
    }
}
