package tn.esprit.examen.nomPrenomClasseExamen.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoForecastResponseDto {
    private Double latitude;
    private Double longitude;
    private OpenMeteoDailyDto daily;
}
