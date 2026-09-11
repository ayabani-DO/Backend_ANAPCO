package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipementService {

    private final EquipementRepository equipementRepo;
    private final SitesRepository sitesRepo;
    private final CategorieEquipementRepository categorieRepo;
    private final IncidentRepository incidentRepo;
    private final MaintenanceRepository maintenanceRepo;

    /**
     * An Equipement must always belong to an existing Site and an existing EquipmentCategory.
     * The referenced entities are resolved server-side before the save.
     */
    public Equipement createEquipement(Equipement equipement) {
        if (equipement == null) {
            throw new IllegalArgumentException("Equipement payload is required");
        }
        Sites site = resolveSite(equipement.getSite(), true);
        equipement.setSite(site);
        equipement.setCategorie(resolveCategorie(equipement.getCategorie(), true));
        // refEquipement is a backend-generated internal reference: any client-supplied value is ignored.
        equipement.setRefEquipement(generateRefEquipement(site));
        // serialNumber is the real physical serial: preserved as supplied, left null when unknown — never fabricated.
        return equipementRepo.save(equipement);
    }

    public Equipement updateEquipement(Long id, Equipement equipement) {
        Equipement e = equipementRepo.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipement", id));
        e.setNomEquipement(equipement.getNomEquipement());
        // refEquipement is immutable once assigned: the stored value is preserved and any incoming
        // value (including null) is ignored — it is never regenerated, even if the Site changes.
        e.setSerialNumber(equipement.getSerialNumber());
        e.setStatusEquipement(equipement.getStatusEquipement());

        // Relationships are only re-pointed when the request supplies one; they are never nulled.
        Sites resolvedSite = resolveSite(equipement.getSite(), false);
        if (resolvedSite != null) {
            e.setSite(resolvedSite);
        }
        CategorieEquipement resolvedCategorie = resolveCategorie(equipement.getCategorie(), false);
        if (resolvedCategorie != null) {
            e.setCategorie(resolvedCategorie);
        }
        return equipementRepo.save(e);
    }

    /**
     * Deletes an Equipement only when no Incident or Maintenance history references it.
     * Operational history is never destroyed by this call.
     */
    public void deleteEquipement(Long id) {
        if (!equipementRepo.existsById(id)) {
            throw ResourceNotFoundException.of("Equipement", id);
        }
        if (incidentRepo.existsByEquipementIdEquipement(id)) {
            throw new DependencyExistsException(
                    "Equipement " + id + " has incident history and cannot be deleted");
        }
        if (maintenanceRepo.existsByEquipementIdEquipement(id)) {
            throw new DependencyExistsException(
                    "Equipement " + id + " has maintenance history and cannot be deleted");
        }
        equipementRepo.deleteById(id);
    }

    public List<Equipement> getAllEquipements() {
        return equipementRepo.findAll();
    }

    public Equipement getEquipementById(Long id) {
        return equipementRepo.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipement", id));
    }

    // ── refEquipement generation ─────────────────────────────────────────

    /**
     * Builds the internal ANAPCO equipment reference for a new Equipement from its (already resolved,
     * managed) owning Site: {@code <site.codeRef>-EQ-<NNN>} with a zero-padded, per-site sequence
     * starting at 1. The sequence starts from {@code countBySiteIdSite + 1} and is incremented until
     * an unused value is found, so pre-existing references are preserved and never collided with.
     * <p>
     * Simple best-effort scheme for the PFE scope: no DB unique constraint and no row locking.
     */
    private String generateRefEquipement(Sites site) {
        String siteCodeRef = site.getCodeRef();
        if (siteCodeRef == null || siteCodeRef.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Site " + site.getIdSite() + " has no codeRef; equipment reference cannot be generated");
        }
        String prefix = siteCodeRef.trim();
        long sequence = equipementRepo.countBySiteIdSite(site.getIdSite()) + 1;
        String candidate = prefix + "-EQ-" + String.format("%03d", sequence);
        while (equipementRepo.existsByRefEquipement(candidate)) {
            sequence++;
            candidate = prefix + "-EQ-" + String.format("%03d", sequence);
        }
        return candidate;
    }

    // ── relationship resolution (STEP 2A) ─────────────────────────────────

    private Sites resolveSite(Sites incoming, boolean required) {
        Long siteId = incoming != null ? incoming.getIdSite() : null;
        if (siteId == null) {
            if (required) {
                throw new IllegalArgumentException("Equipement must reference a site");
            }
            return null;
        }
        return sitesRepo.findById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
    }

    private CategorieEquipement resolveCategorie(CategorieEquipement incoming, boolean required) {
        Long categorieId = incoming != null ? incoming.getIdCategorie() : null;
        if (categorieId == null) {
            if (required) {
                throw new IllegalArgumentException("Equipement must reference an equipment category");
            }
            return null;
        }
        return categorieRepo.findById(categorieId)
                .orElseThrow(() -> ResourceNotFoundException.of("EquipmentCategory", categorieId));
    }
}
