package tn.esprit.examen.nomPrenomClasseExamen.weather.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherData;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherSourceType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherDataRepository extends JpaRepository<WeatherData, Long> {

    List<WeatherData> findBySiteIdSiteOrderByDataDateDesc(Long siteId);

    Optional<WeatherData> findFirstBySiteIdSiteOrderByDataDateDesc(Long siteId);

    Optional<WeatherData> findFirstBySiteIdSiteAndSourceTypeOrderByDataDateDesc(Long siteId, WeatherSourceType sourceType);

    Optional<WeatherData> findBySiteAndDataDateAndSourceType(Sites site, LocalDate dataDate, WeatherSourceType sourceType);
}
