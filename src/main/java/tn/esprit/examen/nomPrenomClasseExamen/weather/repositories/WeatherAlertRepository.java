package tn.esprit.examen.nomPrenomClasseExamen.weather.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.AlertStatus;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherAlert;

import java.util.List;

@Repository
public interface WeatherAlertRepository extends JpaRepository<WeatherAlert, Long> {

    List<WeatherAlert> findBySiteIdSiteOrderByCreatedAtDesc(Long siteId);

    List<WeatherAlert> findBySiteIdSiteAndStatusOrderByCreatedAtDesc(Long siteId, AlertStatus status);

    List<WeatherAlert> findBySiteIdSiteAndCreatedAtBetween(Long siteId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
