package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.CategorieEquipement;

public interface CategorieEquipementRepository extends JpaRepository<CategorieEquipement,Long> {
}
