package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.examen.nomPrenomClasseExamen.entities.MonthlyFeatureSnapshot;

import java.util.List;
import java.util.Optional;

public interface MonthlyFeatureSnapshotRepository extends JpaRepository<MonthlyFeatureSnapshot, Long> {

    Optional<MonthlyFeatureSnapshot> findBySiteIdSiteAndYearAndMonth(Long siteId, Integer year, Integer month);

    List<MonthlyFeatureSnapshot> findBySiteIdSiteOrderByYearDescMonthDesc(Long siteId);

    List<MonthlyFeatureSnapshot> findBySiteIdSiteOrderByYearAscMonthAsc(Long siteId);

    List<MonthlyFeatureSnapshot> findAllByOrderByYearDescMonthDesc();

    @Query("SELECT s FROM MonthlyFeatureSnapshot s WHERE s.nextMonthTotalCostEur IS NOT NULL ORDER BY s.year, s.month")
    List<MonthlyFeatureSnapshot> findAllWithCostTarget();

    @Query("SELECT s FROM MonthlyFeatureSnapshot s WHERE s.riskClass IS NOT NULL ORDER BY s.year, s.month")
    List<MonthlyFeatureSnapshot> findAllWithRiskTarget();
}
