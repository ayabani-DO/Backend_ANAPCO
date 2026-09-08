package tn.esprit.examen.nomPrenomClasseExamen.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter.ConversionResult;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FxRateUnavailableException;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.services.FxRateService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FX foundation contract: one storage convention (EUR-native), one arithmetic (delegated to
 * {@link FxRateService#convert}), and a missing-rate policy that never fabricates a converted value.
 */
@ExtendWith(MockitoExtension.class)
class CurrencyConverterTest {

    @Mock
    private FxRateRepository fxRateRepository;

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter(new FxRateService(fxRateRepository, null));
    }

    private static FxRate eurRow(int year, int month, String toCurrency, double rate) {
        FxRate f = new FxRate();
        f.setYear(year);
        f.setMonth(month);
        f.setFromCurrency("EUR");
        f.setToCurrency(toCurrency);
        f.setRate(rate);
        return f;
    }

    private void storeEurRate(int year, int month, String toCurrency, double rate) {
        lenient().when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(year), eq(month), eq("EUR"), eq(toCurrency)))
                .thenReturn(Optional.of(eurRow(year, month, toCurrency, rate)));
    }

    // A) Stored EUR->GBP = 0.80 ; convert 100 GBP -> EUR ; expect 125 EUR
    @Test
    void convertsForeignAmountIntoReportingCurrency() {
        storeEurRate(2024, 6, "GBP", 0.80);

        ConversionResult r = converter.convert(100.0, "GBP", 2024, 6);

        assertThat(r.converted()).isTrue();
        assertThat(r.amount()).isCloseTo(125.0, within(1e-6)); // 100 / 0.80
        assertThat(r.targetCurrency()).isEqualTo("EUR");
    }

    // B) Stored EUR->TND = 3.40 and EUR->GBP = 0.80 ; convert 340 TND -> GBP ; expect 80 GBP
    @Test
    void crossConvertsThroughEurPivot() {
        ReflectionTestUtils.setField(converter, "configuredReportingCurrency", "GBP");
        storeEurRate(2024, 6, "TND", 3.40);
        storeEurRate(2024, 6, "GBP", 0.80);

        ConversionResult r = converter.convert(340.0, "TND", 2024, 6);

        assertThat(r.converted()).isTrue();
        assertThat(r.amount()).isCloseTo(80.0, within(1e-6)); // 340 / 3.40 = 100 EUR ; 100 * 0.80 = 80 GBP
        assertThat(r.targetCurrency()).isEqualTo("GBP");
    }

    // C) source currency == reporting currency -> amount unchanged, no FX lookup
    @Test
    void sameCurrencyReturnsAmountUnchanged() {
        ConversionResult r = converter.convert(500.0, "eur", 2024, 6);

        assertThat(r.converted()).isTrue();
        assertThat(r.amount()).isEqualTo(500.0);
    }

    // D) Missing FX rate must NOT return the original amount labelled as EUR
    @Test
    void missingRateNeverReturnsRawAmountAsConverted() {
        when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(2024), eq(6), eq("EUR"), eq("GBP"))).thenReturn(Optional.empty());

        ConversionResult r = converter.convert(50_000.0, "GBP", 2024, 6);

        assertThat(r.converted()).isFalse();
        assertThat(r.convertedAmount()).isNaN();
        assertThat(r.originalAmount()).isEqualTo(50_000.0);
        assertThat(r.reason()).contains("missing FX rate", "GBP");
        assertThatThrownBy(r::amount).isInstanceOf(FxRateUnavailableException.class);
        assertThatThrownBy(() -> converter.convertOrThrow(50_000.0, "GBP", 2024, 6))
                .isInstanceOf(FxRateUnavailableException.class)
                .hasMessageContaining("FxRate not found");
    }

    // E) Historical lookup uses the requested business year/month
    @Test
    void usesTheRequestedBusinessMonthForTheRateLookup() {
        storeEurRate(2023, 5, "GBP", 0.50);
        when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(2023), eq(6), eq("EUR"), eq("GBP"))).thenReturn(Optional.empty());

        assertThat(converter.convert(100.0, "GBP", 2023, 5).amount()).isCloseTo(200.0, within(1e-6)); // 100 / 0.50
        assertThat(converter.convert(100.0, "GBP", 2023, 6).converted()).isFalse();     // different month, no rate
    }

    // F) Null / blank currency must not silently become a fake successful conversion
    @Test
    void nullOrBlankCurrencyIsNeverAssumedToBeReportingCurrency() {
        ConversionResult nullCur = converter.convert(100.0, null, 2024, 6);
        ConversionResult blankCur = converter.convert(100.0, "  ", 2024, 6);

        assertThat(nullCur.converted()).isFalse();
        assertThat(blankCur.converted()).isFalse();
        assertThat(nullCur.reason()).contains("unknown source currency");
        assertThatThrownBy(nullCur::amount).isInstanceOf(FxRateUnavailableException.class);
        assertThatThrownBy(() -> converter.convertOrThrow(100.0, null, 2024, 6))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    // A null amount is 0 in every currency — a success, not an FX failure.
    @Test
    void nullAmountIsZeroAndDoesNotTouchFx() {
        ConversionResult r = converter.convert(null, "GBP", 2024, 6);

        assertThat(r.converted()).isTrue();
        assertThat(r.amount()).isZero();
    }

    @Test
    void reportingCurrencyDefaultsToEur() {
        assertThat(converter.reportingCurrency()).isEqualTo("EUR");
    }

    // ── Explicit-target overload (record-scoped / calculator) ──

    @Test
    void targetOverloadSameCurrencyIsIdentity() {
        ConversionResult r = converter.convert(5000.0, "TND", "TND", 2026, 5);
        assertThat(r.converted()).isTrue();
        assertThat(r.amount()).isEqualTo(5000.0);
    }

    @Test
    void targetOverloadConvertsXToEur() {
        storeEurRate(2026, 5, "TND", 3.40);
        ConversionResult r = converter.convert(3400.0, "TND", "EUR", 2026, 5);
        assertThat(r.converted()).isTrue();
        assertThat(r.amount()).isCloseTo(1000.0, within(1e-6));
    }

    @Test
    void targetOverloadCrossesXToYThroughEur() {
        storeEurRate(2026, 5, "TND", 3.40);
        storeEurRate(2026, 5, "GBP", 0.80);
        ConversionResult r = converter.convert(3400.0, "TND", "GBP", 2026, 5);
        assertThat(r.amount()).isCloseTo(800.0, within(1e-6));
    }

    @Test
    void targetOverloadMissingRateIsUnavailable() {
        when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(2026), eq(5), eq("EUR"), eq("TND"))).thenReturn(Optional.empty());
        ConversionResult r = converter.convert(5000.0, "TND", "EUR", 2026, 5);
        assertThat(r.converted()).isFalse();
        assertThat(r.convertedAmount()).isNaN();
        assertThat(r.originalAmount()).isEqualTo(5000.0);
    }

    @Test
    void targetOverloadNullTargetIsUnavailable() {
        ConversionResult r = converter.convert(100.0, "TND", null, 2026, 5);
        assertThat(r.converted()).isFalse();
        assertThat(r.reason()).contains("target currency");
    }
}
