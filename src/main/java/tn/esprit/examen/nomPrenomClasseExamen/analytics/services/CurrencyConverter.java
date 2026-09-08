package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.esprit.examen.nomPrenomClasseExamen.services.FxRateService;

import java.util.Locale;

/**
 * Analytics-facing currency-conversion abstraction.
 *
 * <p>It owns <b>no</b> rate arithmetic — every conversion is delegated to
 * {@link FxRateService#convert(Double, String, String, Integer, Integer)} so there is exactly one
 * implementation of the EUR-pivot maths in the codebase:
 *
 * <pre>
 *   CurrencyConverter  →  FxRateService  →  FxRateRepository
 * </pre>
 *
 * <h2>Storage convention (unchanged, Fixer-native)</h2>
 * FX rows are stored as {@code fromCurrency = EUR}, {@code toCurrency = X},
 * {@code rate = units of X per 1 EUR} — e.g. {@code EUR→GBP = 0.80}. No reverse-direction rows
 * are written or expected.
 *
 * <h2>Missing-rate policy</h2>
 * A missing historical rate NEVER yields the raw local amount labelled as the reporting currency:
 * <ul>
 *   <li>{@link #convert} returns a {@link ConversionResult} whose {@link ConversionResult#converted()}
 *       flag is {@code false}; {@link ConversionResult#amount()} then throws, so the value cannot be
 *       consumed by accident, and {@link ConversionResult#convertedAmount()} is {@code NaN}.</li>
 *   <li>{@link #convertOrThrow} throws {@link FxRateUnavailableException}.</li>
 * </ul>
 *
 * <h2>Historical period policy</h2>
 * A historical business amount is converted with the rate for <b>its own business month</b> — the
 * caller passes {@code businessYear}/{@code businessMonth} (e.g. {@code Incident.date},
 * {@code BudgetMonthly} year/month), never the requested dashboard month.
 *
 * <h2>Reporting currency</h2>
 * Configurable via {@code reporting.currency} (default {@code EUR}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyConverter {

    private static final String DEFAULT_REPORTING_CURRENCY = "EUR";

    private final FxRateService fxRateService;

    @Value("${reporting.currency:EUR}")
    private String configuredReportingCurrency = DEFAULT_REPORTING_CURRENCY;

    /** The canonical reporting currency (upper-case, never blank). */
    public String reportingCurrency() {
        String c = configuredReportingCurrency;
        return (c == null || c.isBlank()) ? DEFAULT_REPORTING_CURRENCY : c.trim().toUpperCase(Locale.ROOT);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Canonical API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Convert a historical business amount into the <b>reporting</b> currency using the rate for the
     * amount's own business month (the API the Analytics / ML layers use).
     *
     * <p>Never throws for a missing rate and never fabricates a value — inspect
     * {@link ConversionResult#converted()}.
     *
     * @param amount         source amount (a {@code null} amount is {@code 0} in every currency —
     *                       this is a successful conversion, not an FX failure)
     * @param sourceCurrency ISO code of the amount's currency; {@code null}/blank is treated as
     *                       "unknown" and yields an unavailable result (never assumed to be the
     *                       reporting currency)
     * @param businessYear   the business year the amount belongs to
     * @param businessMonth  the business month (1–12) the amount belongs to
     */
    public ConversionResult convert(Double amount, String sourceCurrency, int businessYear, int businessMonth) {
        return convert(amount, sourceCurrency, reportingCurrency(), businessYear, businessMonth);
    }

    /**
     * Convert a historical business amount into an <b>explicit</b> target currency, using the rate
     * for the amount's own business month.
     *
     * <p>Same arithmetic ({@link FxRateService#convert}) and same missing-rate policy as the
     * reporting-currency overload — it only lets the caller (record-scoped conversion for a stored
     * record, or the ad-hoc Finance calculator) choose the target. Analytics / ML keep using the
     * reporting-currency overload.
     *
     * @param targetCurrency ISO code to convert into; {@code null}/blank yields an unavailable result
     */
    public ConversionResult convert(Double amount, String sourceCurrency, String targetCurrency,
                                    int businessYear, int businessMonth) {
        String target = normalize(targetCurrency);
        String source = normalize(sourceCurrency);

        if (target == null) {
            return ConversionResult.unavailable(amount == null ? 0d : amount,
                    source != null ? source : "?", "?", "unknown target currency (null/blank)");
        }
        if (amount == null || amount == 0d) {
            // Zero is zero in every currency — no rate needed, never an FX failure.
            return ConversionResult.converted(0d, 0d, source != null ? source : target, target);
        }
        if (source == null) {
            return ConversionResult.unavailable(amount, "?", target,
                    "unknown source currency (null/blank) - refusing to assume the reporting currency");
        }
        if (source.equals(target)) {
            return ConversionResult.converted(amount, amount, source, target);
        }
        try {
            Double result = fxRateService.convert(amount, source, target, businessYear, businessMonth);
            if (result == null || result.isNaN() || result.isInfinite()) {
                return ConversionResult.unavailable(amount, source, target,
                        "conversion produced no usable value for " + source + "->" + target
                                + " " + businessYear + "-" + pad(businessMonth));
            }
            return ConversionResult.converted(result, amount, source, target);
        } catch (RuntimeException ex) {
            log.warn("FX rate unavailable for {}->{} {}-{}: {}", source, target, businessYear, businessMonth, ex.getMessage());
            return ConversionResult.unavailable(amount, source, target,
                    "missing FX rate " + source + "->" + target + " for " + businessYear + "-" + pad(businessMonth));
        }
    }

    /**
     * Strict conversion — throws {@link FxRateUnavailableException} when the rate is missing.
     * Preserves the historical "fail hard" semantics of {@code FinanceKpiService}.
     */
    public double convertOrThrow(Double amount, String sourceCurrency, int businessYear, int businessMonth) {
        ConversionResult r = convert(amount, sourceCurrency, businessYear, businessMonth);
        if (!r.converted()) {
            throw new FxRateUnavailableException("FxRate not found: " + r.reason());
        }
        return r.convertedAmount();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Legacy shims — kept so existing callers compile unchanged during the FX
    // foundation step. Behaviour is now SAFE: neither can fall back to the raw
    // amount, so money can never be silently mislabelled.
    // TODO(canonical-cost step): migrate callers to convert()/ConversionResult
    //   so read-only analytics can degrade gracefully instead of throwing.
    // ────────────────────────────────────────────────────────────────────────

    /**
     * @deprecated use {@link #convertOrThrow(Double, String, int, int)}. Unchanged behaviour:
     * strict, throws on a missing rate.
     */
    @Deprecated
    public double toEur(Long siteId, int year, int month, Double amount, String currencyCode, String siteCurrency) {
        return convertOrThrow(amount, resolve(currencyCode, siteCurrency), year, month);
    }

    /**
     * @deprecated use {@link #convert(Double, String, int, int)} and handle
     * {@link ConversionResult#converted()}.
     *
     * <p><b>Behaviour change:</b> this shim NO LONGER falls back to the raw amount. It now throws
     * {@link FxRateUnavailableException} on a missing rate, exactly like the strict path, so it can
     * never silently persist a local amount into a {@code *Eur} field.
     */
    @Deprecated
    public double toEurOrRaw(int year, int month, Double amount, String currencyCode, String siteCurrency) {
        return convertOrThrow(amount, resolve(currencyCode, siteCurrency), year, month);
    }

    /** record currencyCode → site currency. No implicit reporting-currency fallback. */
    private String resolve(String currencyCode, String siteCurrency) {
        String c = normalize(currencyCode);
        return c != null ? c : normalize(siteCurrency);
    }

    private static String normalize(String currency) {
        return (currency == null || currency.isBlank()) ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String pad(int month) {
        return month < 10 ? "0" + month : String.valueOf(month);
    }

    // ────────────────────────────────────────────────────────────────────────

    /**
     * Outcome of a conversion.
     *
     * <p>{@link #amount()} is only accessible when {@link #converted()} is {@code true}; otherwise it
     * throws {@link FxRateUnavailableException}, and {@link #convertedAmount()} is {@code NaN}, so an
     * unconverted value can never be used by mistake.
     */
    public record ConversionResult(boolean converted,
                                   double convertedAmount,
                                   double originalAmount,
                                   String sourceCurrency,
                                   String targetCurrency,
                                   String reason) {

        static ConversionResult converted(double convertedAmount, double originalAmount,
                                          String source, String target) {
            return new ConversionResult(true, convertedAmount, originalAmount, source, target, null);
        }

        static ConversionResult unavailable(double originalAmount, String source, String target, String reason) {
            return new ConversionResult(false, Double.NaN, originalAmount, source, target, reason);
        }

        /** The converted amount. Throws {@link FxRateUnavailableException} if the conversion failed. */
        public double amount() {
            if (!converted) {
                throw new FxRateUnavailableException(
                        "Conversion " + sourceCurrency + "->" + targetCurrency + " unavailable: " + reason);
            }
            return convertedAmount;
        }
    }
}
