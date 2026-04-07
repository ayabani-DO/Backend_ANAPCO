package tn.esprit.examen.nomPrenomClasseExamen.weather.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherRiskAssessmentDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.services.WeatherRiskService;

import java.util.List;

@RestController
@RequestMapping("/api/weather-risk")
@RequiredArgsConstructor
@Tag(name = "Weather Risk", description = "Rule-based weather risk assessments")
public class WeatherRiskController {

    private final WeatherRiskService weatherRiskService;

    @PostMapping("/sites/{siteId}/assess-latest")
    @Operation(summary = "Assess latest site weather risk", description = "Calculates and persists weather risk score from latest weather data")
    public WeatherRiskAssessmentDto assessLatest(@PathVariable Long siteId) {
        return weatherRiskService.assessLatestWeatherRisk(siteId);
    }

    @GetMapping("/sites/{siteId}/latest")
    @Operation(summary = "Get latest weather risk assessment", description = "Returns the latest saved weather risk assessment for a site")
    public WeatherRiskAssessmentDto getLatest(@PathVariable Long siteId) {
        return weatherRiskService.getLatestAssessment(siteId);
    }

    @GetMapping("/sites/{siteId}")
    @Operation(summary = "Get weather risk history", description = "Returns all persisted weather risk assessments for a site")
    public List<WeatherRiskAssessmentDto> getHistory(@PathVariable Long siteId) {
        return weatherRiskService.getAssessmentHistory(siteId);
    }
}
