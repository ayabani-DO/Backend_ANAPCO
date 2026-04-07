package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.CategorieEquipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
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
                .orElseThrow(() -> new RuntimeException("Categorie non trouvée"));
        c.setNomEquiepment(categorie.getNomEquiepment());
        c.setDescription(categorie.getDescription());
        c.setDateCreation(categorie.getDateCreation());
        return categorieRepo.save(c);
    }


    public void deleteCategorie(Long id) {
        categorieRepo.deleteById(id);
    }


    public List<CategorieEquipement> getAllCategories() {
        return categorieRepo.findAll();
    }


    public CategorieEquipement getCategorieById(Long id) {
        return categorieRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Categorie non trouvée"));
    }

    // Affectation Equipement vers Categorie

    public Equipement affectEquipementToCategorie(Long equipementId, Long categorieId) {
        Equipement e = equipementRepo.findById(equipementId)
                .orElseThrow(() -> new RuntimeException("Equipement non trouvé"));
        CategorieEquipement c = categorieRepo.findById(categorieId)
                .orElseThrow(() -> new RuntimeException("Categorie non trouvée"));
        e.setCategorie(c);
        return equipementRepo.save(e);
    }


    public Set<Equipement> getEquipementsByCategorie(Long categorieId) {
        CategorieEquipement c = categorieRepo.findById(categorieId)
                .orElseThrow(() -> new RuntimeException("Categorie non trouvée"));
        return c.getEquipements();
    }
}
