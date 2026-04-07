package tn.esprit.examen.nomPrenomClasseExamen.weather.services;

import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.RecommendationDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherAlertDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.AlertStatus;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.RiskAssessment;

import java.util.List;

public interface WeatherAdvisoryService {

    void generateForAssessment(RiskAssessment assessment);

    List<WeatherAlertDto> getAlertsBySite(Long siteId, AlertStatus status);

    WeatherAlertDto updateAlertStatus(Long alertId, AlertStatus status);

    List<RecommendationDto> getRecommendationsBySite(Long siteId);
}
