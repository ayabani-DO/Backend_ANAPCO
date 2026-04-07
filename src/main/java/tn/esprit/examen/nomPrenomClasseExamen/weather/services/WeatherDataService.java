package tn.esprit.examen.nomPrenomClasseExamen.weather.services;

import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherDataDto;

import java.util.List;

public interface WeatherDataService {

    List<WeatherDataDto> syncForecast(Long siteId, int daysAhead);

    List<WeatherDataDto> syncHistorical(Long siteId, int daysBack);

    List<WeatherDataDto> getBySite(Long siteId);

    WeatherDataDto getLatestBySite(Long siteId);
}
