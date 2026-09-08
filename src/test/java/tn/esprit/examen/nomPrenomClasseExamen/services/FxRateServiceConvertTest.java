package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Locks the single canonical FX arithmetic. Storage rows are EUR-native
 * ({@code fromCurrency = EUR, toCurrency = X, rate = X per 1 EUR}); every direction pivots through EUR.
 */
@ExtendWith(MockitoExtension.class)
class FxRateServiceConvertTest {

    @Mock
    private FxRateRepository fxRateRepository;

    private FxRateService service;

    @BeforeEach
    void setUp() {
        service = new FxRateService(fxRateRepository, null);
    }

    private void storeEurRate(int year, int month, String toCurrency, double rate) {
        FxRate f = new FxRate();
        f.setYear(year);
        f.setMonth(month);
        f.setFromCurrency("EUR");
        f.setToCurrency(toCurrency);
        f.setRate(rate);
        lenient().when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(year), eq(month), eq("EUR"), eq(toCurrency))).thenReturn(Optional.of(f));
    }

    @Test
    void sameCurrencyIsIdentity() {
        assertThat(service.convert(100.0, "EUR", "EUR", 2024, 6)).isEqualTo(100.0);
    }

    @Test
    void eurToForeignMultiplies() {
        storeEurRate(2024, 6, "GBP", 0.80);
        assertThat(service.convert(100.0, "EUR", "GBP", 2024, 6)).isCloseTo(80.0, within(1e-6));
    }

    @Test
    void foreignToEurDivides() {
        storeEurRate(2024, 6, "GBP", 0.80);
        assertThat(service.convert(100.0, "GBP", "EUR", 2024, 6)).isCloseTo(125.0, within(1e-6));
    }

    @Test
    void foreignToForeignCrossesThroughEur() {
        storeEurRate(2024, 6, "TND", 3.40);
        storeEurRate(2024, 6, "GBP", 0.80);
        assertThat(service.convert(340.0, "TND", "GBP", 2024, 6)).isCloseTo(80.0, within(1e-6));
    }

    @Test
    void missingRateThrows() {
        assertThatThrownBy(() -> service.convert(100.0, "GBP", "EUR", 2024, 6))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing FX rate for EUR->GBP");
    }

    @Test
    void nullRateValueThrows() {
        FxRate f = new FxRate();
        f.setYear(2024);
        f.setMonth(6);
        f.setFromCurrency("EUR");
        f.setToCurrency("GBP");
        f.setRate(null);
        lenient().when(fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                eq(2024), eq(6), eq("EUR"), eq("GBP"))).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.convert(100.0, "GBP", "EUR", 2024, 6))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("is null");
    }
}
