package tn.esprit.examen.nomPrenomClasseExamen.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenMeteoDailyDto {
    private List<String> time;
    private List<Double> temperature_2m_max;
    private List<Double> wind_speed_10m_max;
    private List<Double> precipitation_sum;
    private List<Double> visibility_mean;
    private List<Double> relative_humidity_2m_mean;
    private List<Integer> weather_code;
}
