package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FxRateService {

    private final FxRateRepository fxRateRepository;
    private final RestTemplate restTemplate;

    @Value("${fixer.api.key}")
    private String fixerApiKey;

    private static final String FIXER_BASE_URL = "https://data.fixer.io/api";
    private static final String EUR = "EUR";

    // ==================== EXISTING CRUD METHODS ====================

    public FxRate create(FxRate fxRate) {
        return fxRateRepository.save(fxRate);
    }

    public FxRate update(Long id, FxRate fxRate) {
        FxRate f = fxRateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("FxRate not found"));

        f.setYear(fxRate.getYear());
        f.setMonth(fxRate.getMonth());
        f.setFromCurrency(fxRate.getFromCurrency());
        f.setToCurrency(fxRate.getToCurrency());
        f.setRate(fxRate.getRate());
        return fxRateRepository.save(f);
    }

    public void delete(Long id) {
        fxRateRepository.deleteById(id);
    }

    public List<FxRate> getAll() {
        return fxRateRepository.findAll();
    }

    public FxRate getById(Long id) {
        return fxRateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("FxRate not found"));
    }

    public FxRate getByYearMonthAndPair(Integer year, Integer month, String fromCurrency, String toCurrency) {
        return fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                        year,
                        month,
                        fromCurrency,
                        toCurrency
                )
                .orElseThrow(() -> new RuntimeException("FxRate not found"));
    }

    // ==================== FIXER API INTEGRATION ====================

    public FxRate fetchAndSaveLatestRate(String toCurrency) {
        String normalizedCurrency = normalizeCurrency(toCurrency);
        String url = FIXER_BASE_URL + "/latest?access_key=" + fixerApiKey + "&symbols=" + normalizedCurrency;

        Map<String, Object> response = callFixer(url);
        Double rate = extractRate(response, normalizedCurrency);

        LocalDate now = LocalDate.now();
        return upsertFxRate(EUR, normalizedCurrency, rate, now.getYear(), now.getMonthValue());
    }

    public FxRate fetchAndSaveHistoricalRate(String toCurrency, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        String normalizedCurrency = normalizeCurrency(toCurrency);
        String dateString = parseFixerDate(date);
        String url = FIXER_BASE_URL + "/" + dateString + "?access_key=" + fixerApiKey + "&symbols=" + normalizedCurrency;

        Map<String, Object> response = callFixer(url);
        Double rate = extractRate(response, normalizedCurrency);

        return upsertFxRate(EUR, normalizedCurrency, rate, date.getYear(), date.getMonthValue());
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> callFixer(String url) {
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Fixer API returned null response");
            }

            Boolean success = (Boolean) response.get("success");
            if (Boolean.FALSE.equals(success)) {
                String errorInfo = response.containsKey("error") ? response.get("error").toString() : "Unknown error";
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Fixer API error: " + errorInfo);
            }

            return response;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call Fixer API: " + e.getMessage(), e);
        }
    }

    private Double extractRate(Map<String, Object> response, String currency) {
        Object ratesObj = response.get("rates");
        if (!(ratesObj instanceof Map)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid rates format in response");
        }
        Map<?, ?> rates = (Map<?, ?>) ratesObj;
        Object rateValue = rates.get(currency);
        if (rateValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rate not found for currency: " + currency);
        }
        if (rateValue instanceof Number) {
            return ((Number) rateValue).doubleValue();
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid rate format for currency: " + currency);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String parseFixerDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return String.format("%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private FxRate upsertFxRate(String fromCurrency, String toCurrency, Double rate, Integer year, Integer month) {
        FxRate fxRate = fxRateRepository
                .findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(year, month, fromCurrency, toCurrency)
                .orElse(new FxRate());

        fxRate.setYear(year);
        fxRate.setMonth(month);
        fxRate.setFromCurrency(fromCurrency);
        fxRate.setToCurrency(toCurrency);
        fxRate.setRate(rate);

        return fxRateRepository.save(fxRate);
    }

    // ==================== CONVERSION METHOD ====================

    public Double convert(Double amount, String from, String to, Integer year, Integer month) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (year == null || month == null) {
            throw new IllegalArgumentException("Year and month cannot be null");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        String fromCurrency = normalizeCurrency(from);
        String toCurrency = normalizeCurrency(to);

        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        if (EUR.equals(fromCurrency)) {
            // EUR -> TO: direct multiplication
            FxRate toRate = fxRateRepository
                    .findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                            year, month, EUR, toCurrency
                    )
                    .orElseThrow(() -> new RuntimeException("Missing FX rate for " + toCurrency));
            return amount * toRate.getRate();
        }

        if (EUR.equals(toCurrency)) {
            // FROM -> EUR: divide by EUR->FROM rate
            FxRate fromRate = fxRateRepository
                    .findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                            year, month, EUR, fromCurrency
                    )
                    .orElseThrow(() -> new RuntimeException("Missing FX rate for " + fromCurrency));
            return amount / fromRate.getRate();
        }

        // FROM -> EUR -> TO: cross conversion via EUR
        FxRate fromRate = fxRateRepository
                .findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                        year, month, EUR, fromCurrency
                )
                .orElseThrow(() -> new RuntimeException("Missing FX rate for " + fromCurrency));

        FxRate toRate = fxRateRepository
                .findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                        year, month, EUR, toCurrency
                )
                .orElseThrow(() -> new RuntimeException("Missing FX rate for " + toCurrency));

        double amountInEur = amount / fromRate.getRate();
        return amountInEur * toRate.getRate();
    }
}
