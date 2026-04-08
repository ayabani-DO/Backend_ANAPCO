package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.services.MaintenanceService;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@Tag(name = "Maintenance", description = "Gestion des maintenances des équipements")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @PostMapping("/create")
    @Operation(summary = "Créer une maintenance")
    public Maintenance create(@RequestBody Maintenance maintenance) {
        return maintenanceService.create(maintenance);
    }

    @PutMapping("/update/{id}")
    @Operation(summary = "Mettre à jour une maintenance")
    public Maintenance update(@PathVariable Long id, @RequestBody Maintenance maintenance) {
        return maintenanceService.update(id, maintenance);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "Supprimer une maintenance")
    public void delete(@PathVariable Long id) {
        maintenanceService.delete(id);
    }

    @GetMapping("/getAll")
    @Operation(summary = "Récupérer toutes les maintenances")
    public List<Maintenance> getAll() {
        return maintenanceService.getAllMaintenance();
    }

    @GetMapping("/getById/{id}")
    @Operation(summary = "Récupérer une maintenance par ID")
    public Maintenance getById(@PathVariable Long id) {
        return maintenanceService.getById(id);
    }

    @GetMapping("/getByEquipement/{equipementId}")
    @Operation(summary = "Récupérer les maintenances d'un équipement")
    public List<Maintenance> getByEquipement(@PathVariable Long equipementId) {
        return maintenanceService.getByEquipement(equipementId);
    }

    @GetMapping("/getByEquipement/{equipementId}/type/{type}")
    @Operation(summary = "Récupérer les maintenances d'un équipement filtrées par type")
    public List<Maintenance> getByEquipementAndType(
            @PathVariable Long equipementId,
            @PathVariable TypeMaintenance type) {
        return maintenanceService.getByEquipementAndType(equipementId, type);
    }

    @GetMapping("/getByEquipement/{equipementId}/status/{status}")
    @Operation(summary = "Récupérer les maintenances d'un équipement filtrées par statut")
    public List<Maintenance> getByEquipementAndStatus(
            @PathVariable Long equipementId,
            @PathVariable StatusMaintenace status) {
        return maintenanceService.getByEquipementAndStatus(equipementId, status);
    }

    @GetMapping("/getBySite/{siteId}")
    @Operation(summary = "Récupérer toutes les maintenances d'un site")
    public List<Maintenance> getBySite(@PathVariable Long siteId) {
        return maintenanceService.getBySite(siteId);
    }
}
