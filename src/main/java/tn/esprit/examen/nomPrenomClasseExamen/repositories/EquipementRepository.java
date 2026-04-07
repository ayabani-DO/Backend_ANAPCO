package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;

import java.util.List;

@Repository
public interface EquipementRepository extends JpaRepository<Equipement,Long> {

    List<Equipement> findBySiteCodeRefAndCategorieNomEquiepment(String siteCodeRef, String categorieNom);
    
    List<Equipement> findBySiteCodeRef(String siteCodeRef);
    
    List<Equipement> findByCategorieNomEquiepment(String categorieNom);

    List<Equipement> findBySiteIdSite(Long siteId);
}
