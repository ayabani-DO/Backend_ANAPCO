package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ManualExpenseRepository extends JpaRepository<ManualExpense, Long> {
    List<ManualExpense> findBySite_IdSite(Long siteId);

    List<ManualExpense> findBySite_IdSiteAndDateBetween(Long siteId, LocalDate start, LocalDate end);
}
