package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;

import java.util.Date;
import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance,Long> {

    List<Maintenance> findByEquipementIdEquipement(Long equipementId);

    List<Maintenance> findByEquipementIdEquipementAndDateBetween(Long equipementId, Date startDate, Date endDate);

    List<Maintenance> findByEquipementIdEquipementAndStatusMaintenance(Long equipementId, StatusMaintenace status);

    List<Maintenance> findByEquipementIdEquipementAndStatusMaintenanceAndDateBetween(
            Long equipementId, StatusMaintenace status, Date startDate, Date endDate);

    List<Maintenance> findByEquipementIdEquipementAndTypeMaintenance(Long equipementId, TypeMaintenance type);

    List<Maintenance> findByEquipementIdEquipementAndTypeMaintenanceAndDateBetween(
            Long equipementId, TypeMaintenance type, Date startDate, Date endDate);

    List<Maintenance> findByEquipementSiteIdSite(Long siteId);

    List<Maintenance> findByEquipementSiteIdSiteAndDateBetween(Long siteId, Date startDate, Date endDate);
}
