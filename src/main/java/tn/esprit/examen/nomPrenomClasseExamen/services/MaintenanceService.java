package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;

import java.util.List;

@Service
public class MaintenanceService {

    @Autowired
    MaintenanceRepository maintenanceRepository;

    public List<Maintenance> getAllMaintenance() {
        return maintenanceRepository.findAll();
    }

    public Maintenance getById(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Maintenance not found: " + id));
    }

    public Maintenance create(Maintenance maintenance) {
        return maintenanceRepository.save(maintenance);
    }

    public Maintenance update(Long id, Maintenance maintenance) {
        Maintenance existing = getById(id);
        existing.setRefCode(maintenance.getRefCode());
        existing.setTypeMaintenance(maintenance.getTypeMaintenance());
        existing.setStatusMaintenance(maintenance.getStatusMaintenance());
        existing.setDate(maintenance.getDate());
        existing.setDescription(maintenance.getDescription());
        existing.setCostReal(maintenance.getCostReal());
        return maintenanceRepository.save(existing);
    }

    public void delete(Long id) {
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
}
