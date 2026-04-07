package tn.esprit.examen.nomPrenomClasseExamen.weather.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WeatherSiteOverviewDto {
    private Long siteId;
    private WeatherDataDto latestWeatherData;
    private WeatherRiskAssessmentDto latestAssessment;
    private List<WeatherAlertDto> alerts;
    private List<RecommendationDto> recommendations;
}
