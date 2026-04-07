package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.services.IncidentService;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incident", description = "Gestion des incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService incidentService;

    //Affecter un incident à un site
    @PostMapping("/affectIncidentToSite/{incidentId}/site/{siteId}")
    public Incident affectIncidentToSite(
            @PathVariable Long incidentId,
            @PathVariable Long siteId) {

        return incidentService.affecterIncidentToSite(incidentId, siteId);
    }
}
