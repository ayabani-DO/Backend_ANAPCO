package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.CategorieEquipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.exception.DependencyExistsException;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.CategorieEquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategorieEquipementService {


    private final CategorieEquipementRepository categorieRepo;
    private final EquipementRepository equipementRepo;


    public CategorieEquipement createCategorie(CategorieEquipement categorie) {
        return categorieRepo.save(categorie);
    }


    public CategorieEquipement updateCategorie(Long id, CategorieEquipement categorie) {
        CategorieEquipement c = categorieRepo.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("EquipmentCategory", id));
        c.setNomEquiepment(categorie.getNomEquiepment());
        c.setDescription(categorie.getDescription());
        c.setDateCreation(categorie.getDateCreation());
        return categorieRepo.save(c);
    }


    /**
     * Deletes a category only when no Equipement still references it. Referencing equipment
     * causes a 409 Conflict and is never deleted.
     */
    public void deleteCategorie(Long id) {
        if (!categorieRepo.existsById(id)) {
            throw ResourceNotFoundException.of("EquipmentCategory", id);
        }
        if (equipementRepo.existsByCategorieIdCategorie(id)) {
            throw new DependencyExistsException(
                    "EquipmentCategory " + id + " is still referenced by equipment and cannot be deleted");
        }
        categorieRepo.deleteById(id);
    }


    public List<CategorieEquipement> getAllCategories() {
        return categorieRepo.findAll();
    }


    public CategorieEquipement getCategorieById(Long id) {
        return categorieRepo.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("EquipmentCategory", id));
    }

    // Affectation Equipement vers Categorie

    public Equipement affectEquipementToCategorie(Long equipementId, Long categorieId) {
        Equipement e = equipementRepo.findById(equipementId)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipement", equipementId));
        CategorieEquipement c = categorieRepo.findById(categorieId)
                .orElseThrow(() -> ResourceNotFoundException.of("EquipmentCategory", categorieId));
        e.setCategorie(c);
        return equipementRepo.save(e);
    }


    public Set<Equipement> getEquipementsByCategorie(Long categorieId) {
        CategorieEquipement c = categorieRepo.findById(categorieId)
                .orElseThrow(() -> ResourceNotFoundException.of("EquipmentCategory", categorieId));
        return c.getEquipements();
    }
}
