package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.exception.ResourceNotFoundException;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetMonthlyService {

    private final BudgetMonthlyRepository budgetMonthlyRepository;
    private final SitesRepository sitesRepository;

    public BudgetMonthly create(BudgetMonthly budgetMonthly, Long siteId) {
        Sites site = sitesRepository.findById(siteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
        budgetMonthly.setSite(site);
        return budgetMonthlyRepository.save(budgetMonthly);
    }

    public BudgetMonthly update(Long id, BudgetMonthly budgetMonthly) {
        BudgetMonthly b = budgetMonthlyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));

        b.setYear(budgetMonthly.getYear());
        b.setMonth(budgetMonthly.getMonth());
        b.setAmount(budgetMonthly.getAmount());
        b.setCurrencyCode(budgetMonthly.getCurrencyCode());

        if (budgetMonthly.getSite() != null && budgetMonthly.getSite().getIdSite() != null) {
            Long siteId = budgetMonthly.getSite().getIdSite();
            Sites site = sitesRepository.findById(siteId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Site", siteId));
            b.setSite(site);
        }

        return budgetMonthlyRepository.save(b);
    }

    public void delete(Long id) {
        budgetMonthlyRepository.deleteById(id);
    }

    public List<BudgetMonthly> getAll() {
        return budgetMonthlyRepository.findAll();
    }

    public BudgetMonthly getById(Long id) {
        return budgetMonthlyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Budget not found"));
    }

    public List<BudgetMonthly> getBySite(Long siteId) {
        return budgetMonthlyRepository.findBySite_IdSite(siteId);
    }
}
