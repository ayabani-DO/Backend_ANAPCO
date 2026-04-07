package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final SitesRepository sitesRepository;
    private final EquipementRepository equipementRepository;

    public Incident affecterIncidentToSite(Long incidentId, Long siteId) {

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident introuvable"));

        Sites site = sitesRepository.findById(siteId)
                .orElseThrow(() -> new RuntimeException("Site introuvable"));

        // 🔥 AFFECTATION AVANT SAVE
        incident.setSites(site);

        return incidentRepository.save(incident);
    }
}
