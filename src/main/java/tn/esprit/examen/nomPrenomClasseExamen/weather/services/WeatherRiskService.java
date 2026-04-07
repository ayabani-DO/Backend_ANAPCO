package tn.esprit.examen.nomPrenomClasseExamen.weather.services;

import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherRiskAssessmentDto;

import java.util.List;

public interface WeatherRiskService {

    WeatherRiskAssessmentDto assessLatestWeatherRisk(Long siteId);

    WeatherRiskAssessmentDto getLatestAssessment(Long siteId);

    List<WeatherRiskAssessmentDto> getAssessmentHistory(Long siteId);
}
