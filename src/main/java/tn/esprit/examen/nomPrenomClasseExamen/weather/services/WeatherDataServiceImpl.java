package tn.esprit.examen.nomPrenomClasseExamen.weather.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.client.OpenMeteoClient;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.OpenMeteoDailyDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.OpenMeteoForecastResponseDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherDataDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherData;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherSourceType;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.WeatherDataRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
public class WeatherDataServiceImpl implements WeatherDataService {

    private final WeatherDataRepository weatherDataRepository;
    private final SitesRepository sitesRepository;
    private final OpenMeteoClient openMeteoClient;

    @Override
    public List<WeatherDataDto> syncForecast(Long siteId, int daysAhead) {
        Sites site = findSite(siteId);
        int safeDays = Math.max(1, daysAhead);
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(safeDays - 1L);

        OpenMeteoForecastResponseDto response = openMeteoClient.getForecast(site.getLatitude(), site.getLongitude(), start, end);
        return upsertDailyData(site, response.getDaily(), WeatherSourceType.FORECAST);
    }

    @Override
    public List<WeatherDataDto> syncHistorical(Long siteId, int daysBack) {
        Sites site = findSite(siteId);
        int safeDays = Math.max(1, daysBack);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(safeDays - 1L);

        OpenMeteoForecastResponseDto response = openMeteoClient.getHistorical(site.getLatitude(), site.getLongitude(), start, end);
        return upsertDailyData(site, response.getDaily(), WeatherSourceType.HISTORICAL);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeatherDataDto> getBySite(Long siteId) {
        return weatherDataRepository.findBySiteIdSiteOrderByDataDateDesc(siteId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WeatherDataDto getLatestBySite(Long siteId) {
        WeatherData weatherData = weatherDataRepository.findFirstBySiteIdSiteOrderByDataDateDesc(siteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No weather data found for site id: " + siteId));
        return toDto(weatherData);
    }

    private Sites findSite(Long siteId) {
        return sitesRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Site not found with id: " + siteId));
    }

    private List<WeatherDataDto> upsertDailyData(Sites site, OpenMeteoDailyDto daily, WeatherSourceType sourceType) {
        List<String> dates = daily.getTime();
        List<WeatherDataDto> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

        for (int i = 0; i < dates.size(); i++) {
            LocalDate dataDate = LocalDate.parse(dates.get(i), formatter);

            WeatherData weatherData = weatherDataRepository
                    .findBySiteAndDataDateAndSourceType(site, dataDate, sourceType)
                    .orElse(WeatherData.builder()
                            .site(site)
                            .dataDate(dataDate)
                            .sourceType(sourceType)
                            .build());

            weatherData.setTemperatureC(getDouble(daily.getTemperature_2m_max(), i));
            weatherData.setWindSpeedKmh(getDouble(daily.getWind_speed_10m_max(), i));
            weatherData.setPrecipitationMm(getDouble(daily.getPrecipitation_sum(), i));
            weatherData.setVisibilityKm(getDouble(daily.getVisibility_mean(), i));
            weatherData.setHumidityPercent(getDouble(daily.getRelative_humidity_2m_mean(), i));
            weatherData.setWeatherCode(getInteger(daily.getWeather_code(), i));

            result.add(toDto(weatherDataRepository.save(weatherData)));
        }

        return result;
    }

    private Double getDouble(List<Double> values, int index) {
        if (values == null || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private Integer getInteger(List<Integer> values, int index) {
        if (values == null || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private WeatherDataDto toDto(WeatherData weatherData) {
        return WeatherDataDto.builder()
                .id(weatherData.getId())
                .siteId(weatherData.getSite().getIdSite())
                .sourceType(weatherData.getSourceType())
                .dataDate(weatherData.getDataDate())
                .temperatureC(weatherData.getTemperatureC())
                .windSpeedKmh(weatherData.getWindSpeedKmh())
                .precipitationMm(weatherData.getPrecipitationMm())
                .visibilityKm(weatherData.getVisibilityKm())
                .humidityPercent(weatherData.getHumidityPercent())
                .weatherCode(weatherData.getWeatherCode())
                .fetchedAt(weatherData.getFetchedAt())
                .build();
    }
}
