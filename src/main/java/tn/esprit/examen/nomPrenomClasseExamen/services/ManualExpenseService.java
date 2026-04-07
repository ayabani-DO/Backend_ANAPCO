package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ManualExpenseService {

    private final ManualExpenseRepository manualExpenseRepository;
    private final SitesRepository sitesRepository;

    public ManualExpense create(ManualExpense manualExpense, Long siteId) {
        Sites site = sitesRepository.findById(siteId)
                .orElseThrow(() -> new RuntimeException("Site not found"));
        manualExpense.setSite(site);
        return manualExpenseRepository.save(manualExpense);
    }

    public ManualExpense update(Long id, ManualExpense manualExpense) {
        ManualExpense m = manualExpenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ManualExpense not found"));

        m.setDate(manualExpense.getDate());
        m.setCategory(manualExpense.getCategory());
        m.setAmount(manualExpense.getAmount());
        m.setCurrencyCode(manualExpense.getCurrencyCode());

        if (manualExpense.getSite() != null && manualExpense.getSite().getIdSite() != null) {
            Sites site = sitesRepository.findById(manualExpense.getSite().getIdSite())
                    .orElseThrow(() -> new RuntimeException("Site not found"));
            m.setSite(site);
        }

        return manualExpenseRepository.save(m);
    }

    public void delete(Long id) {
        manualExpenseRepository.deleteById(id);
    }

    public List<ManualExpense> getAll() {
        return manualExpenseRepository.findAll();
    }

    public ManualExpense getById(Long id) {
        return manualExpenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ManualExpense not found"));
    }

    public List<ManualExpense> getBySite(Long siteId) {
        return manualExpenseRepository.findBySite_IdSite(siteId);
    }

    public List<ManualExpense> getBySiteAndPeriod(Long siteId, LocalDate start, LocalDate end) {
        return manualExpenseRepository.findBySite_IdSiteAndDateBetween(siteId, start, end);
    }
}
