package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;

/**
 * Single source of truth for currency-to-EUR conversion.
 *
 * <p>Exposes two modes so each caller keeps its original semantics while sharing one implementation:
 * <ul>
 *   <li>{@link #toEur} — <b>strict</b>: throws when the FX rate is missing (finance KPI semantics);</li>
 *   <li>{@link #toEurOrRaw} — <b>lenient</b>: falls back to the raw amount and logs (analytics / ML semantics).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyConverter {

    public static final String BASE_CURRENCY = "EUR";

    private final FxRateRepository fxRateRepository;

    /**
     * Strict conversion to EUR. Throws {@link RuntimeException} if the rate is missing — preserves the
     * exact behaviour (and message) of the legacy {@code FinanceKpiService}.
     */
    public double toEur(Long siteId, int year, int month, Double amount, String currencyCode, String siteCurrency) {
        if (amount == null) return 0d;
        String effective = effectiveCurrency(currencyCode, siteCurrency);
        if (isBaseOrBlank(effective)) return amount;

        FxRate fxRate = fxRateRepository
                .findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(year, month, effective, BASE_CURRENCY)
                .orElseThrow(() -> new RuntimeException(
                        "FxRate not found for conversion " + effective + "->EUR (siteId=" + siteId
                                + ", year=" + year + ", month=" + month + ")"));
        if (fxRate.getRate() == null) {
            throw new RuntimeException("FxRate.rate is null for conversion " + effective + "->EUR");
        }
        return amount * fxRate.getRate();
    }

    /**
     * Lenient conversion to EUR. Missing/blank rate falls back to the raw amount (logged) so read-only
     * analytics and feature computation never fail hard.
     */
    public double toEurOrRaw(int year, int month, Double amount, String currencyCode, String siteCurrency) {
        if (amount == null) return 0d;
        String effective = effectiveCurrency(currencyCode, siteCurrency);
        if (isBaseOrBlank(effective)) return amount;

        FxRate fxRate = fxRateRepository
                .findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(year, month, effective, BASE_CURRENCY)
                .orElse(null);
        if (fxRate != null && fxRate.getRate() != null) {
            return amount * fxRate.getRate();
        }
        log.warn("FxRate not found for {}->EUR ({}-{}); using raw amount.", effective, year, month);
        return amount;
    }

    private String effectiveCurrency(String currencyCode, String siteCurrency) {
        return (currencyCode == null || currencyCode.isBlank()) ? siteCurrency : currencyCode;
    }

    private boolean isBaseOrBlank(String currency) {
        return currency == null || currency.isBlank() || BASE_CURRENCY.equalsIgnoreCase(currency);
    }
}
