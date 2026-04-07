package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

@Service
public class SitesService {

    @Autowired
    private SitesRepository sitesRepository;

    public Sites createSite(Sites site) {
        // Auto-generate codeRef if not provided
        if (site.getCodeRef() == null || site.getCodeRef().isEmpty()) {
            if (site.getCountryCode() != null && !site.getCountryCode().isEmpty()) {
                long count = sitesRepository.countByCountryCode(site.getCountryCode());
                String codeRef = site.getCountryCode() + "-PLANT-" + (count + 1);
                site.setCodeRef(codeRef);
            }
        }
        return sitesRepository.save(site);
    }
}
