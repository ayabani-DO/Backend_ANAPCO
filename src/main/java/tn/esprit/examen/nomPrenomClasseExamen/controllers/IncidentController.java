package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.services.IncidentService;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incident", description = "Gestion des incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService incidentService;

    // CRUD Incident
    @PostMapping("/create")
    public Incident createIncident(@RequestBody Incident incident) {
        return incidentService.createIncident(incident);
    }

    @GetMapping("/getAll")
    public List<Incident> getAllIncidents() {
        return incidentService.getAllIncidents();
    }

    @GetMapping("/getById/{id}")
    public Incident getIncidentById(@PathVariable Long id) {
        return incidentService.getIncidentById(id);
    }

    @PutMapping("/update/{id}")
    public Incident updateIncident(@PathVariable Long id, @RequestBody Incident incident) {
        return incidentService.updateIncident(id, incident);
    }

    @DeleteMapping("/delete/{id}")
    public void deleteIncident(@PathVariable Long id) {
        incidentService.deleteIncident(id);
    }

    //Affecter un incident à un site
    @PostMapping("/affectIncidentToSite/{incidentId}/site/{siteId}")
    public Incident affectIncidentToSite(
            @PathVariable Long incidentId,
            @PathVariable Long siteId) {

        return incidentService.affecterIncidentToSite(incidentId, siteId);
    }
}
