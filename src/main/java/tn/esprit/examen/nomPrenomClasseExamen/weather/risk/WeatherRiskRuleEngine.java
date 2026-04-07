package tn.esprit.examen.nomPrenomClasseExamen.weather.risk;

import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.SensitivityLevel;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.SiteType;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherData;

public interface WeatherRiskRuleEngine {
    RiskComputationResult compute(WeatherData weatherData, SiteType siteType, SensitivityLevel sensitivityLevel);
}
