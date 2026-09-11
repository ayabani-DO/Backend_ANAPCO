package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final EquipementRepository equipementRepository;

    public List<Maintenance> getAllMaintenance() {
        return maintenanceRepository.findAll();
    }

    public Maintenance getById(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Maintenance", id));
    }

    /**
     * A Maintenance must reference an existing Equipement, and that Equipement must itself belong
     * to a Site. Basic field validation: the date is mandatory and, when present, {@code costReal}
     * must not be negative.
     */
    public Maintenance create(Maintenance maintenance) {
        if (maintenance == null) {
            throw new IllegalArgumentException("Maintenance payload is required");
        }
        validateFields(maintenance);
        maintenance.setEquipement(resolveEquipement(maintenance.getEquipement()));
        return maintenanceRepository.save(maintenance);
    }

    public Maintenance update(Long id, Maintenance maintenance) {
        Maintenance existing = getById(id);
        validateFields(maintenance);

        existing.setRefCode(maintenance.getRefCode());
        existing.setTypeMaintenance(maintenance.getTypeMaintenance());
        existing.setStatusMaintenance(maintenance.getStatusMaintenance());
        existing.setDate(maintenance.getDate());
        existing.setDescription(maintenance.getDescription());
        existing.setCostReal(maintenance.getCostReal());

        // Only re-point the equipment when the request supplies one; it is never nulled.
        if (maintenance.getEquipement() != null && maintenance.getEquipement().getIdEquipement() != null) {
            existing.setEquipement(resolveEquipement(maintenance.getEquipement()));
        }
        return maintenanceRepository.save(existing);
    }

    public void delete(Long id) {
        if (!maintenanceRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Maintenance", id);
        }
        maintenanceRepository.deleteById(id);
    }

    public List<Maintenance> getByEquipement(Long equipementId) {
        return maintenanceRepository.findByEquipementIdEquipement(equipementId);
    }

    public List<Maintenance> getByEquipementAndType(Long equipementId, TypeMaintenance type) {
        return maintenanceRepository.findByEquipementIdEquipementAndTypeMaintenance(equipementId, type);
    }

    public List<Maintenance> getByEquipementAndStatus(Long equipementId, StatusMaintenace status) {
        return maintenanceRepository.findByEquipementIdEquipementAndStatusMaintenance(equipementId, status);
    }

    public List<Maintenance> getBySite(Long siteId) {
        return maintenanceRepository.findByEquipementSiteIdSite(siteId);
    }

    // ── validation helpers (STEP 2A) ───────────────────────────────────────

    private void validateFields(Maintenance maintenance) {
        if (maintenance.getDate() == null) {
            throw new IllegalArgumentException("Maintenance date is required");
        }
        if (maintenance.getCostReal() != null && maintenance.getCostReal() < 0) {
            throw new IllegalArgumentException("Maintenance costReal must be >= 0");
        }
    }

    private Equipement resolveEquipement(Equipement incoming) {
        Long equipementId = incoming != null ? incoming.getIdEquipement() : null;
        if (equipementId == null) {
            throw new IllegalArgumentException("Maintenance must reference an equipment");
        }
        Equipement equipement = equipementRepository.findById(equipementId)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipement", equipementId));
        if (equipement.getSite() == null) {
            throw new IllegalArgumentException(
                    "Referenced equipment " + equipementId + " has no site; maintenance cannot be recorded");
        }
        return equipement;
    }
}
