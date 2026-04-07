package tn.esprit.examen.nomPrenomClasseExamen.market.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tn.esprit.examen.nomPrenomClasseExamen.entities.OilPriceRecord;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fetches Brent crude oil prices from the U.S. EIA (Energy Information Administration) open API.
 * Free API key: https://www.eia.gov/opendata/register.php
 * Series: PET.RBRTE.D (Europe Brent Spot Price FOB, daily, USD/barrel)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OilPriceClientImpl implements OilPriceClient {

    private static final String SOURCE = "EIA_API";
    private static final String SERIES_ID = "RBRTE";
    private static final DateTimeFormatter EIA_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;

    @Value("${eia.api.key:}")
    private String apiKey;

    @Value("${eia.api.base-url:https://api.eia.gov/v2}")
    private String baseUrl;

    @Override
    public OilPriceRecord fetchLatestPrice() {
        List<OilPriceRecord> records = fetchFromEia(null, null, 1);
        if (records.isEmpty()) {
            log.warn("EIA API returned no data for latest Brent price");
            return null;
        }
        return records.get(0);
    }

    @Override
    public List<OilPriceRecord> fetchPriceRange(LocalDate start, LocalDate end) {
        return fetchFromEia(start, end, 5000);
    }

    private List<OilPriceRecord> fetchFromEia(LocalDate start, LocalDate end, int length) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("EIA API key not configured (eia.api.key). Skipping oil price fetch.");
            return Collections.emptyList();
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/petroleum/pri/spt/data/")
                    .queryParam("api_key", apiKey)
                    .queryParam("frequency", "daily")
                    .queryParam("data[0]", "value")
                    .queryParam("facets[series][]", SERIES_ID)
                    .queryParam("sort[0][column]", "period")
                    .queryParam("sort[0][direction]", "desc")
                    .queryParam("length", length);

            if (start != null) {
                builder.queryParam("start", start.format(EIA_DATE));
            }
            if (end != null) {
                builder.queryParam("end", end.format(EIA_DATE));
            }

            URI uri = builder.build(false).toUri();
            EiaResponse response = restTemplate.getForObject(uri, EiaResponse.class);

            if (response == null || response.response == null || response.response.data == null) {
                log.warn("EIA API returned empty payload");
                return Collections.emptyList();
            }

            List<OilPriceRecord> result = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (EiaDataPoint dp : response.response.data) {
                if (dp.period == null || dp.value == null) continue;
                try {
                    double price = Double.parseDouble(dp.value);
                    result.add(OilPriceRecord.builder()
                            .priceDate(LocalDate.parse(dp.period))
                            .priceUsd(price)
                            .source(SOURCE)
                            .fetchedAt(now)
                            .build());
                } catch (NumberFormatException e) {
                    log.warn("Skipping EIA data point with invalid price: period={}, value={}", dp.period, dp.value);
                }
            }
            return result;

        } catch (Exception e) {
            log.error("Failed to fetch oil prices from EIA API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ── EIA JSON response mapping ──────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EiaResponse {
        private EiaResponseBody response;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EiaResponseBody {
        private List<EiaDataPoint> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EiaDataPoint {
        private String period;
        private String value;
        @JsonProperty("series")
        private String series;
    }
}
