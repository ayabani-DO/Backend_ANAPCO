package tn.esprit.examen.nomPrenomClasseExamen.weather.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.RecommendationDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherAlertDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherDataDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherSiteOverviewDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.AlertStatus;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherAdvisoryService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherDataService;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.util.List;

@RestController
@RequestMapping("/api/weather-advisories")
@RequiredArgsConstructor
@Tag(name = "Weather Advisory", description = "Weather alerts and recommendations")
public class WeatherAdvisoryController {

    private final WeatherAdvisoryService weatherAdvisoryService;
    private final WeatherRiskService weatherRiskService;
    private final WeatherDataService weatherDataService;

    @GetMapping("/sites/{siteId}/overview")
    @Operation(summary = "Get complete weather overview", description = "Returns latest risk assessment, alerts, recommendations, and latest weather data for a site")
    public WeatherSiteOverviewDto getOverview(@PathVariable Long siteId) {
        WeatherDataDto latestWeatherData = null;
        try {
            latestWeatherData = weatherDataService.getLatestBySite(siteId);
        } catch (ResponseStatusException ignored) {
        }

        WeatherSiteOverviewDto.WeatherSiteOverviewDtoBuilder builder = WeatherSiteOverviewDto.builder()
                .siteId(siteId)
                .latestWeatherData(latestWeatherData)
                .alerts(weatherAdvisoryService.getAlertsBySite(siteId, null))
                .recommendations(weatherAdvisoryService.getRecommendationsBySite(siteId));

        try {
            builder.latestAssessment(weatherRiskService.getLatestAssessment(siteId));
        } catch (ResponseStatusException ignored) {
        }

        return builder.build();
    }

    @GetMapping("/sites/{siteId}/alerts")
    @Operation(summary = "Get weather alerts by site", description = "Returns weather alerts for a site, optionally filtered by status")
    public List<WeatherAlertDto> getAlerts(
            @PathVariable Long siteId,
            @RequestParam(required = false) AlertStatus status) {
        return weatherAdvisoryService.getAlertsBySite(siteId, status);
    }

    @PutMapping("/alerts/{alertId}/status")
    @Operation(summary = "Update weather alert status", description = "Updates OPEN/ACKNOWLEDGED/RESOLVED status of an alert")
    public WeatherAlertDto updateStatus(
            @PathVariable Long alertId,
            @RequestParam AlertStatus status) {
        return weatherAdvisoryService.updateAlertStatus(alertId, status);
    }

    @GetMapping("/sites/{siteId}/recommendations")
    @Operation(summary = "Get weather recommendations by site", description = "Returns generated weather recommendations for a site")
    public List<RecommendationDto> getRecommendations(@PathVariable Long siteId) {
        return weatherAdvisoryService.getRecommendationsBySite(siteId);
    }
}
