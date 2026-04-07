package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetMonthlyRepository extends JpaRepository<BudgetMonthly, Long> {
    List<BudgetMonthly> findBySite_IdSite(Long siteId);

    Optional<BudgetMonthly> findBySite_IdSiteAndYearAndMonth(Long siteId, Integer year, Integer month);
}
