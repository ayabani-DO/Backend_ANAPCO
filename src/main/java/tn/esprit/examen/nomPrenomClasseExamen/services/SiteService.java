package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.DependencyExistsException;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SitesRepository siteRepository;
    private final IncidentRepository incidentRepository;
    private final EquipementRepository equipementRepository;
    private final BudgetMonthlyRepository budgetMonthlyRepository;
    private final ManualExpenseRepository manualExpenseRepository;

    public Sites createSite(Sites site) {
        validateSite(site);
        // codeRef is a backend-generated business identifier: any client-supplied value is ignored.
        site.setCodeRef(generateCodeRef(site.getCountryCode()));
        return siteRepository.save(site);
    }

    public Sites updateSite(Long idSite, Sites site) {
        Sites s = siteRepository.findById(idSite)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", idSite));

        validateSite(site);

        // codeRef is immutable once assigned: the stored value is preserved and any
        // incoming codeRef (including null) is ignored — it is never regenerated here either.
        s.setNom(site.getNom());
        s.setLatitude(site.getLatitude());
        s.setLongitude(site.getLongitude());
        s.setCountryCode(site.getCountryCode());
        s.setCurrencyCode(site.getCurrencyCode());
        s.setStatusSites(site.getStatusSites());

        return siteRepository.save(s);
    }

    /**
     * Deletes a Site only when it carries no operational or financial history.
     * Dependent Equipment, Incidents, Budgets or Manual Expenses cause a 409 Conflict;
     * they are never cascade-deleted.
     */
    public void deleteSite(Long idSite) {
        if (!siteRepository.existsById(idSite)) {
            throw ResourceNotFoundException.of("Site", idSite);
        }
        if (equipementRepository.existsBySiteIdSite(idSite)) {
            throw new DependencyExistsException(
                    "Site " + idSite + " still has equipment and cannot be deleted");
        }
        if (incidentRepository.existsBySitesIdSite(idSite)) {
            throw new DependencyExistsException(
                    "Site " + idSite + " still has incident history and cannot be deleted");
        }
        if (budgetMonthlyRepository.existsBySite_IdSite(idSite)) {
            throw new DependencyExistsException(
                    "Site " + idSite + " still has budget records and cannot be deleted");
        }
        if (manualExpenseRepository.existsBySite_IdSite(idSite)) {
            throw new DependencyExistsException(
                    "Site " + idSite + " still has manual expenses and cannot be deleted");
        }
        siteRepository.deleteById(idSite);
    }

    public List<Sites> getAllSites() {
        return siteRepository.findAll();
    }

    public Sites getSiteById(Long idSite) {
        return siteRepository.findById(idSite)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", idSite));
    }

    // Affecter Incident à Site
    public Incident affectIncidentToSite(Long idIncident, Long idSite) {

        Incident incident = incidentRepository.findById(idIncident)
                .orElseThrow(() -> ResourceNotFoundException.of("Incident", idIncident));

        Sites site = siteRepository.findById(idSite)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", idSite));

        assertConsistentWithEquipmentSite(incident, idSite);

        incident.setSites(site);

        return incidentRepository.save(incident);
    }

    // Site → Incidents
    public Set<Incident> getIncidentsBySite(Long idSite) {
        Sites site = siteRepository.findById(idSite)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", idSite));

        return site.getLincident();
    }

    // ── validation helpers (STEP 2A) ───────────────────────────────────────

    /**
     * Minimum information required by the downstream analytics layer. A missing currency is
     * rejected outright — it is never silently defaulted to EUR. {@code codeRef} is intentionally
     * NOT validated here: it is backend-generated on create and immutable on update.
     */
    private void validateSite(Sites site) {
        if (site == null) {
            throw new IllegalArgumentException("Site payload is required");
        }
        if (isBlank(site.getNom())) {
            throw new IllegalArgumentException("Site nom is required");
        }
        if (isBlank(site.getCountryCode())) {
            throw new IllegalArgumentException("Site countryCode is required and must not be blank");
        }
        if (isBlank(site.getCurrencyCode())) {
            throw new IllegalArgumentException("Site currencyCode is required and must not be blank");
        }
        if (site.getStatusSites() == null) {
            throw new IllegalArgumentException("Site statusSites is required");
        }
        Double lat = site.getLatitude();
        if (lat != null && (lat < -90.0 || lat > 90.0)) {
            throw new IllegalArgumentException("Site latitude must be within [-90, 90]");
        }
        Double lon = site.getLongitude();
        if (lon != null && (lon < -180.0 || lon > 180.0)) {
            throw new IllegalArgumentException("Site longitude must be within [-180, 180]");
        }
    }

    /**
     * Generates a stable, human-readable business identifier for a new Site, e.g. {@code TN-PLANT-1}.
     * <p>
     * The country component comes from the (already validated non-blank) {@code countryCode},
     * trimmed and upper-cased. The sequence starts from {@code countByCountryCode + 1} and is then
     * incremented until an unused value is found, so pre-existing codeRefs — including legacy ones
     * that do not follow this pattern (e.g. {@code TN-GAS-01}) — are preserved and never collided with.
     * <p>
     * This is a simple, best-effort scheme appropriate for the PFE scope: no DB unique constraint
     * and no row locking are introduced here.
     */
    private String generateCodeRef(String countryCode) {
        String country = countryCode.trim().toUpperCase();
        long sequence = siteRepository.countByCountryCode(country) + 1;
        String candidate = country + "-PLANT-" + sequence;
        while (siteRepository.existsByCodeRef(candidate)) {
            sequence++;
            candidate = country + "-PLANT-" + sequence;
        }
        return candidate;
    }

    /**
     * If the incident is already linked to a piece of equipment, the site it is being attached to
     * must be that equipment's site (STEP 2A cross-site guard).
     */
    static void assertConsistentWithEquipmentSite(Incident incident, Long targetSiteId) {
        Equipement equipement = incident.getEquipement();
        if (equipement == null) {
            return;
        }
        Sites equipmentSite = equipement.getSite();
        if (equipmentSite == null || equipmentSite.getIdSite() == null) {
            throw new IllegalArgumentException(
                    "Incident equipment has no site; the incident cannot be attached to a site");
        }
        if (!equipmentSite.getIdSite().equals(targetSiteId)) {
            throw new DependencyExistsException(
                    "Incident equipment belongs to a different site (" + equipmentSite.getIdSite()
                            + "); it cannot be attached to site " + targetSiteId);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
