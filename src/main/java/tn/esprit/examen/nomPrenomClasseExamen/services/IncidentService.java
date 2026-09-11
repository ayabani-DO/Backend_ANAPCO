package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final SitesRepository sitesRepository;
    private final EquipementRepository equipementRepository;

    public Incident affecterIncidentToSite(Long incidentId, Long siteId) {

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Incident", incidentId));

        Sites site = sitesRepository.findById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));

        // Guard: if the incident already carries an equipment, the target site must be that
        // equipment's site, and the equipment must have a site at all (STEP 2A).
        SiteService.assertConsistentWithEquipmentSite(incident, siteId);

        incident.setSites(site);

        return incidentRepository.save(incident);
    }

    // ── CRUD (STEP 2B) ──────────────────────────────────────────────────────

    /**
     * An Incident always belongs to a Site. Equipment is optional; when supplied, it must exist,
     * must itself belong to a Site, and that Site must match the Incident's Site (STEP 2A/2B
     * cross-site guard, reused as-is from {@link SiteService}).
     */
    public Incident createIncident(Incident incident) {
        if (incident == null) {
            throw new IllegalArgumentException("Incident payload is required");
        }
        validateCoreFields(incident);

        Sites site = resolveSite(incident.getSites(), true);
        Equipement equipement = resolveOptionalEquipement(incident.getEquipement());

        incident.setSites(site);
        incident.setEquipement(equipement);

        SiteService.assertConsistentWithEquipmentSite(incident, site.getIdSite());

        return incidentRepository.save(incident);
    }

    public Incident getIncidentById(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Incident", id));
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    /**
     * Relationships are resolved server-side and only re-pointed when the request actually
     * supplies them; an omitted {@code sites}/{@code equipement} never silently nulls the
     * existing link (the nested request object is never trusted as a managed JPA reference).
     */
    public Incident updateIncident(Long id, Incident incoming) {
        Incident existing = getIncidentById(id);
        if (incoming == null) {
            throw new IllegalArgumentException("Incident payload is required");
        }
        validateCoreFields(incoming);

        existing.setSeverityCode(incoming.getSeverityCode());
        existing.setEtatIncident(incoming.getEtatIncident());
        existing.setDate(incoming.getDate());
        existing.setDescription(incoming.getDescription());
        existing.setCostEstimated(incoming.getCostEstimated());
        existing.setCostReal(incoming.getCostReal());
        existing.setClosedDate(incoming.getClosedDate());

        Sites resolvedSite = resolveSite(incoming.getSites(), false);
        if (resolvedSite != null) {
            existing.setSites(resolvedSite);
        }
        if (incoming.getEquipement() != null) {
            existing.setEquipement(resolveOptionalEquipement(incoming.getEquipement()));
        }

        if (existing.getSites() == null) {
            throw new IllegalArgumentException("Incident must reference a site");
        }
        SiteService.assertConsistentWithEquipmentSite(existing, existing.getSites().getIdSite());

        return incidentRepository.save(existing);
    }

    /**
     * Inspection confirmed no downstream entity holds a hard foreign key to Incident, so unlike
     * Site/Equipement/CategorieEquipement, deleting an Incident needs no dependency guard.
     */
    public void deleteIncident(Long id) {
        if (!incidentRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Incident", id);
        }
        incidentRepository.deleteById(id);
    }

    // ── validation / resolution helpers (STEP 2B) ────────────────────────────

    private void validateCoreFields(Incident incident) {
        if (incident.getDate() == null) {
            throw new IllegalArgumentException("Incident date is required");
        }
        if (incident.getCostEstimated() != null && incident.getCostEstimated() < 0) {
            throw new IllegalArgumentException("Incident costEstimated must be >= 0");
        }
        if (incident.getCostReal() != null && incident.getCostReal() < 0) {
            throw new IllegalArgumentException("Incident costReal must be >= 0");
        }
        if (incident.getEtatIncident() == EtatIncident.CLOSED) {
            if (incident.getClosedDate() == null) {
                throw new IllegalArgumentException(
                        "Incident closedDate is required when etatIncident is CLOSED");
            }
            if (incident.getClosedDate().before(incident.getDate())) {
                throw new IllegalArgumentException("Incident closedDate cannot precede date");
            }
        }
    }

    /**
     * Site is mandatory on create; on update a missing/partial reference means "keep the
     * existing site" ({@code required = false}) rather than an error or a silent null-out.
     */
    private Sites resolveSite(Sites incoming, boolean required) {
        Long siteId = incoming != null ? incoming.getIdSite() : null;
        if (siteId == null) {
            if (required) {
                throw new IllegalArgumentException("Incident must reference a site");
            }
            return null;
        }
        return sitesRepository.findById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
    }

    /**
     * Equipment is always optional on an Incident: an entirely omitted reference simply leaves
     * the incident site-level. But a supplied reference is not allowed to be empty — if the
     * client sends an {@code equipement} object at all, {@code idEquipement} is mandatory within
     * it, so a bare {@code "equipement": {}} is rejected rather than silently ignored.
     */
    private Equipement resolveOptionalEquipement(Equipement incoming) {
        if (incoming == null) {
            return null;
        }
        Long equipementId = incoming.getIdEquipement();
        if (equipementId == null) {
            throw new IllegalArgumentException("Incident equipment reference must include idEquipement");
        }
        Equipement equipement = equipementRepository.findById(equipementId)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipement", equipementId));
        if (equipement.getSite() == null) {
            throw new IllegalArgumentException(
                    "Referenced equipment " + equipementId + " has no site; it cannot be attached to an incident");
        }
        return equipement;
    }
}
