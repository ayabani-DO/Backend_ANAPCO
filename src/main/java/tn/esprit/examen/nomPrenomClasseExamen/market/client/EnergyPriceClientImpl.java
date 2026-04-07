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
import tn.esprit.examen.nomPrenomClasseExamen.entities.EnergyPriceRecord;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fetches energy prices from the EIA API v2.
 * - Gas: Henry Hub Natural Gas Spot Price ($/MMBtu) → converted to €/MWh
 * - Electricity: derived from gas price × configurable multiplier
 *
 * The countryCode parameter is accepted for interface compatibility but
 * all records use the same global Henry Hub benchmark price.
 *
 * Docs: https://www.eia.gov/opendata/documentation.php
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnergyPriceClientImpl implements EnergyPriceClient {

    private static final String SOURCE = "EIA_API";
    private static final String GAS_SERIES = "RNGWHHD";
    private static final DateTimeFormatter EIA_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;

    @Value("${eia.api.key:}")
    private String apiKey;

    @Value("${eia.api.base-url:https://api.eia.gov/v2}")
    private String baseUrl;

    /** Conversion factor: $/MMBtu → €/MWh.  1 MMBtu = 0.293 MWh → ×3.412, then USD→EUR ×0.92 ≈ 3.14 */
    @Value("${energy.usd-mmbtu-to-eur-mwh:3.14}")
    private double gasConversionFactor;

    /** Electricity = gas × this multiplier (EU markets typically 2.5–3.5×) */
    @Value("${energy.electricity-gas-multiplier:2.8}")
    private double electricityGasMultiplier;

    @Override
    public EnergyPriceRecord fetchLatestPrice(String countryCode) {
        List<EnergyPriceRecord> records = fetchFromEia(countryCode, null, null, 1);
        if (records.isEmpty()) return null;
        return records.get(0);
    }

    @Override
    public List<EnergyPriceRecord> fetchPriceRange(String countryCode, LocalDate start, LocalDate end) {
        return fetchFromEia(countryCode, start, end, 5000);
    }

    private List<EnergyPriceRecord> fetchFromEia(String countryCode, LocalDate start, LocalDate end, int length) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("EIA API key not configured (eia.api.key). Skipping energy price fetch.");
            return Collections.emptyList();
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/natural-gas/pri/fut/data/")
                    .queryParam("api_key", apiKey)
                    .queryParam("frequency", "daily")
                    .queryParam("data[0]", "value")
                    .queryParam("facets[series][]", GAS_SERIES)
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
                log.warn("EIA API returned empty payload for energy prices");
                return Collections.emptyList();
            }

            List<EnergyPriceRecord> result = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (EiaDataPoint dp : response.response.data) {
                if (dp.period == null || dp.value == null) continue;
                try {
                    double gasUsdMmbtu = Double.parseDouble(dp.value);
                    double gasEurMwh = Math.round(gasUsdMmbtu * gasConversionFactor * 100.0) / 100.0;
                    double elecEurMwh = Math.round(gasEurMwh * electricityGasMultiplier * 100.0) / 100.0;

                    result.add(EnergyPriceRecord.builder()
                            .priceDate(LocalDate.parse(dp.period))
                            .countryCode(countryCode.toUpperCase())
                            .gasPriceEurMwh(gasEurMwh)
                            .electricityPriceEurMwh(elecEurMwh)
                            .source(SOURCE)
                            .fetchedAt(now)
                            .build());
                } catch (NumberFormatException e) {
                    log.warn("Skipping EIA gas data point with invalid value: period={}, value={}", dp.period, dp.value);
                }
            }
            log.info("EIA energy fetch for {}: {} records", countryCode, result.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to fetch energy prices from EIA API: {}", e.getMessage(), e);
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
