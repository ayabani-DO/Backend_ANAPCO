package tn.esprit.examen.nomPrenomClasseExamen.weather.client;

import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.OpenMeteoForecastResponseDto;

import java.time.LocalDate;

public interface OpenMeteoClient {

    OpenMeteoForecastResponseDto getForecast(Double latitude, Double longitude, LocalDate startDate, LocalDate endDate);

    OpenMeteoForecastResponseDto getHistorical(Double latitude, Double longitude, LocalDate startDate, LocalDate endDate);
}
