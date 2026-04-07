package tn.esprit.examen.nomPrenomClasseExamen.weather.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.RiskAssessment;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    List<RiskAssessment> findBySiteIdSiteOrderByAssessedAtDesc(Long siteId);

    Optional<RiskAssessment> findFirstBySiteIdSiteOrderByAssessedAtDesc(Long siteId);

    Optional<RiskAssessment> findFirstByWeatherDataId(Long weatherDataId);

    List<RiskAssessment> findBySiteIdSiteAndAssessedAtBetween(Long siteId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
