package tn.esprit.examen.nomPrenomClasseExamen.weather.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.OpenMeteoForecastResponseDto;

import java.net.URI;
import java.time.LocalDate;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
@RequiredArgsConstructor
public class OpenMeteoClientImpl implements OpenMeteoClient {

    private static final String DAILY_PARAMS = "temperature_2m_max,wind_speed_10m_max,precipitation_sum,visibility_mean,relative_humidity_2m_mean,weather_code";

    private final RestTemplate restTemplate;

    @Value("${weather.open-meteo.forecast-url:https://api.open-meteo.com/v1/forecast}")
    private String forecastUrl;

    @Value("${weather.open-meteo.historical-url:https://archive-api.open-meteo.com/v1/archive}")
    private String historicalUrl;

    @Value("${weather.open-meteo.default-timezone:auto}")
    private String timezone;

    @Override
    public OpenMeteoForecastResponseDto getForecast(Double latitude, Double longitude, LocalDate startDate, LocalDate endDate) {
        return fetch(buildUri(forecastUrl, latitude, longitude, startDate, endDate));
    }

    @Override
    public OpenMeteoForecastResponseDto getHistorical(Double latitude, Double longitude, LocalDate startDate, LocalDate endDate) {
        return fetch(buildUri(historicalUrl, latitude, longitude, startDate, endDate));
    }

    private OpenMeteoForecastResponseDto fetch(URI uri) {
        try {
            OpenMeteoForecastResponseDto response = restTemplate.getForObject(uri, OpenMeteoForecastResponseDto.class);
            if (response == null || response.getDaily() == null || response.getDaily().getTime() == null) {
                throw new ResponseStatusException(BAD_GATEWAY, "Open-Meteo returned empty weather payload");
            }
            return response;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to call Open-Meteo API: " + e.getMessage(), e);
        }
    }

    private URI buildUri(String baseUrl, Double latitude, Double longitude, LocalDate startDate, LocalDate endDate) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate)
                .queryParam("daily", DAILY_PARAMS)
                .queryParam("timezone", timezone)
                .build(true)
                .toUri();
    }
}
