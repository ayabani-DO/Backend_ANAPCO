package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.CategorieEquipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.services.CategorieEquipementService;
import tn.esprit.examen.nomPrenomClasseExamen.services.EquipementService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Equipement & Catégorie")

public class EquipementController {
    private final EquipementService equipementService;
    private final CategorieEquipementService categorieService;

    // CRUD Equipement
    @PostMapping("/Equipement/createEquipement")
    public Equipement createEquipement(@RequestBody Equipement e) {
        return equipementService.createEquipement(e);
    }

    @PutMapping("/Equipement/updateEquipement/{id}")
    public Equipement updateEquipement(@PathVariable Long id, @RequestBody Equipement e) {
        return equipementService.updateEquipement(id, e);
    }

    @DeleteMapping("/Equipement/deleteEquipement/{id}")
    public void deleteEquipement(@PathVariable Long id) {
        equipementService.deleteEquipement(id);
    }

    @GetMapping("/Equipement/getAllEquipements")
    public List<Equipement> getAllEquipements() {
        return equipementService.getAllEquipements();
    }

    @GetMapping("/Equipement/getEquipementById/{id}")
    public Equipement getEquipementById(@PathVariable Long id) {
        return equipementService.getEquipementById(id);
    }

    // CRUD Categorie
    @PostMapping("/categorie/createCategorie")
    public CategorieEquipement createCategorie(@RequestBody CategorieEquipement c) {
        return categorieService.createCategorie(c);
    }

    @PutMapping("/categorie/updateCategorie/{id}")
    public CategorieEquipement updateCategorie(@PathVariable Long id, @RequestBody CategorieEquipement c) {
        return categorieService.updateCategorie(id, c);
    }

    @DeleteMapping("/categorie/deleteCategorie/{id}")
    public void deleteCategorie(@PathVariable Long id) {
        categorieService.deleteCategorie(id);
    }

    @GetMapping("/categorie/getAllCategories")
    public List<CategorieEquipement> getAllCategories() {
        return categorieService.getAllCategories();
    }

    @GetMapping("/categorie/getCategorieById/{id}")
    public CategorieEquipement getCategorieById(@PathVariable Long id) {
        return categorieService.getCategorieById(id);
    }

    // Affectation Equipement Vers Categorie
    @PutMapping("/affectEquipementToCategorie/{equipementId}/categorie/{categorieId}")
    public Equipement affectEquipementToCategorie(
            @PathVariable Long equipementId,
            @PathVariable Long categorieId) {

        return categorieService.affectEquipementToCategorie(equipementId, categorieId);
    }

    // Liste Equipements d’une catégorie
    @GetMapping("/getEquipementsByCategorie/{categorieId}")
    public Set<Equipement> getEquipementsByCategorie(@PathVariable Long categorieId) {
        return categorieService.getEquipementsByCategorie(categorieId);
    }

}
