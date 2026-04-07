package tn.esprit.examen.nomPrenomClasseExamen.weather.dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherSourceType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class WeatherDataDto {
    private Long id;
    private Long siteId;
    private WeatherSourceType sourceType;
    private LocalDate dataDate;
    private Double temperatureC;
    private Double windSpeedKmh;
    private Double precipitationMm;
    private Double visibilityKm;
    private Double humidityPercent;
    private Integer weatherCode;
    private LocalDateTime fetchedAt;
}
