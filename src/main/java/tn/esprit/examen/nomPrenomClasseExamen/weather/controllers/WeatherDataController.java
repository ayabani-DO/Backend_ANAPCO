package tn.esprit.examen.nomPrenomClasseExamen.weather.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherDataDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherDataService;

import java.util.List;

@RestController
@RequestMapping("/api/weather-data")
@RequiredArgsConstructor
@Tag(name = "Weather Data", description = "Open-Meteo data sync and persistence")
public class WeatherDataController {

    private final WeatherDataService weatherDataService;

    @PostMapping("/sites/{siteId}/sync/forecast")
    @Operation(summary = "Sync forecast weather", description = "Fetches forecast data from Open-Meteo and persists it")
    public List<WeatherDataDto> syncForecast(
            @PathVariable Long siteId,
            @RequestParam(defaultValue = "7") int daysAhead) {
        return weatherDataService.syncForecast(siteId, daysAhead);
    }

    @PostMapping("/sites/{siteId}/sync/historical")
    @Operation(summary = "Sync historical weather", description = "Fetches historical data from Open-Meteo archive and persists it")
    public List<WeatherDataDto> syncHistorical(
            @PathVariable Long siteId,
            @RequestParam(defaultValue = "30") int daysBack) {
        return weatherDataService.syncHistorical(siteId, daysBack);
    }

    @GetMapping("/sites/{siteId}")
    @Operation(summary = "Get weather data by site", description = "Returns persisted weather data for a site")
    public List<WeatherDataDto> getBySite(@PathVariable Long siteId) {
        return weatherDataService.getBySite(siteId);
    }

    @GetMapping("/sites/{siteId}/latest")
    @Operation(summary = "Get latest weather data", description = "Returns latest persisted weather data for a site")
    public WeatherDataDto getLatestBySite(@PathVariable Long siteId) {
        return weatherDataService.getLatestBySite(siteId);
    }
}
