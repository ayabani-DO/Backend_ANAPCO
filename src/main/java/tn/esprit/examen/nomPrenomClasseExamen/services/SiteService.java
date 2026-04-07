package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SitesRepository siteRepository;
    private final IncidentRepository incidentRepository;


    public Sites createSite(Sites site) {
        return siteRepository.save(site);
    }


    public Sites updateSite(Long idSite, Sites site) {
        Sites s = siteRepository.findById(idSite)
                .orElseThrow(() -> new RuntimeException("Site not found"));

        s.setCodeRef(site.getCodeRef());
        s.setNom(site.getNom());
        s.setLatitude(site.getLatitude());
        s.setLongitude(site.getLongitude());
        s.setCountryCode(site.getCountryCode());
        s.setCurrencyCode(site.getCurrencyCode());
        s.setStatusSites(site.getStatusSites());

        return siteRepository.save(s);
    }


    public void deleteSite(Long idSite) {
        siteRepository.deleteById(idSite);
    }


    public List<Sites> getAllSites() {
        return siteRepository.findAll();
    }


    public Sites getSiteById(Long idSite) {
        return siteRepository.findById(idSite)
                .orElseThrow(() -> new RuntimeException("Site not found"));
    }

    // Affecter Incident à Site
    public Incident affectIncidentToSite(Long idIncident, Long idSite) {

        Incident incident = incidentRepository.findById(idIncident)
                .orElseThrow(() -> new RuntimeException("Incident not found"));

        Sites site = siteRepository.findById(idSite)
                .orElseThrow(() -> new RuntimeException("Site not found"));

        incident.setSites(site);

        return incidentRepository.save(incident);
    }

    // Site → Incidents

    public Set<Incident> getIncidentsBySite(Long idSite) {
        Sites site = siteRepository.findById(idSite)
                .orElseThrow(() -> new RuntimeException("Site not found"));

        return site.getLincident();
    }

}
