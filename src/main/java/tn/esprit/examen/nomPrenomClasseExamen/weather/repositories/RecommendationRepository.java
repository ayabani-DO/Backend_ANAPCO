package tn.esprit.examen.nomPrenomClasseExamen.weather.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.Recommendation;

import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findBySiteIdSiteOrderByCreatedAtDesc(Long siteId);

    List<Recommendation> findByRiskAssessmentIdOrderByCreatedAtDesc(Long riskAssessmentId);
}
