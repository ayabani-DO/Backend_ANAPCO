package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.services.SiteService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@Tag(name = "Site")
public class SiteController {

    private final SiteService siteService;

    // Create
    @PostMapping("/createSite")
    public Sites createSite(@RequestBody Sites site) {
        return siteService.createSite(site);
    }

    //Update
    @PutMapping("/updateSite/{idSite}")
    public Sites updateSite(
            @PathVariable Long idSite,
            @RequestBody Sites site) {

        return siteService.updateSite(idSite, site);
    }

    // Delete
    @DeleteMapping("/deleteSite/{idSite}")
    public void deleteSite(@PathVariable Long idSite) {
        siteService.deleteSite(idSite);
    }

    // Get all
    @GetMapping("/getAllSites")
    public List<Sites> getAllSites() {
        return siteService.getAllSites();
    }

    //Get by id
    @GetMapping("/getSiteById/GetById/{idSite}")
    public Sites getSiteById(@PathVariable Long idSite) {
        return siteService.getSiteById(idSite);
    }

    // Affecter Incident à Site
    @PutMapping("/affectIncidentToSite/{siteId}/incident/{incidentId}")
    public Incident  affectIncidentToSite(
            @PathVariable Long siteId,
            @PathVariable Long incidentId) {

        return siteService.affectIncidentToSite(incidentId, siteId);
    }

    //Site → Incidents
    @GetMapping("/getIncidentsBySite/{siteId}/incidents")
    public Set<Incident> getIncidentsBySite(
            @PathVariable Long siteId) {

        return siteService.getIncidentsBySite(siteId);
    }
}
