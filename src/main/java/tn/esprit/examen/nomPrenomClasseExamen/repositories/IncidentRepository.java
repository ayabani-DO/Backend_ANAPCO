package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident,Long> {
    
    List<Incident> findByEquipementSiteCodeRefAndEquipementCategorieNomEquiepment(String siteCodeRef, String categorieNom);
    
    List<Incident> findByEquipementSiteCodeRef(String siteCodeRef);
    
    List<Incident> findByEquipementCategorieNomEquiepment(String categorieNom);
    
    List<Incident> findBySitesIdSiteAndDateBetween(Long siteId, Date startDate, Date endDate);
    
    List<Incident> findByEquipementIdEquipementAndDateBetween(Long equipmentId, Date startDate, Date endDate);

    List<Incident> findByEquipementIdEquipement(Long equipmentId);
}
