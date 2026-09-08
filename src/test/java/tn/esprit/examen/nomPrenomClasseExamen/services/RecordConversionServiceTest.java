package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.dto.ConvertedMoneyDto;
import tn.esprit.examen.nomPrenomClasseExamen.entities.*;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Record-scoped, display-only conversion: the caller supplies only {id} + target currency; the
 * backend derives amount, source currency and business month from the record. Missing rate /
 * currency / date → explicit unavailable, never a fabricated value.
 */
@ExtendWith(MockitoExtension.class)
class RecordConversionServiceTest {

    @Mock private BudgetMonthlyRepository budgetMonthlyRepository;
    @Mock private ManualExpenseRepository manualExpenseRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private FxRateRepository fxRateRepository;

    private RecordConversionService service;

    @BeforeEach
    void setUp() {
        CurrencyConverter cc = new CurrencyConverter(new FxRateService(fxRateRepository, null));
        service = new RecordConversionService(budgetMonthlyRepository, manualExpenseRepository,
                incidentRepository, maintenanceRepository, fxRateRepository, cc);
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

    private static Sites site(String currency) {
        Sites s = new Sites();
        s.setIdSite(1L);
        s.setCurrencyCode(currency);
        return s;
    }

    private static Date date(int y, int m, int d) {
        return Date.from(LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    // A ── Budget record conversion: user gives only target currency
    @Test
    void convertsBudgetRecordDerivingAmountSourceAndPeriod() {
        storeEurRate(2026, 5, "TND", 3.40);
        BudgetMonthly b = new BudgetMonthly();
        b.setId(10L);
        b.setAmount(3400.0);
        b.setCurrencyCode("TND");
        b.setYear(2026);
        b.setMonth(5);
        when(budgetMonthlyRepository.findById(10L)).thenReturn(Optional.of(b));

        ConvertedMoneyDto dto = service.convertBudget(10L, "EUR");

        assertThat(dto.getRecordType()).isEqualTo("BUDGET_MONTHLY");
        assertThat(dto.getOriginalAmount()).isEqualTo(3400.0);
        assertThat(dto.getOriginalCurrency()).isEqualTo("TND");
        assertThat(dto.getTargetCurrency()).isEqualTo("EUR");
        assertThat(dto.getPeriod()).isEqualTo("2026-05");
        assertThat(dto.isRateAvailable()).isTrue();
        assertThat(dto.getConvertedAmount()).isCloseTo(1000.0, within(1e-6));
    }

    // B ── ManualExpense record conversion
    @Test
    void convertsManualExpenseRecord() {
        storeEurRate(2026, 5, "GBP", 0.80);
        ManualExpense e = new ManualExpense();
        e.setId(17L);
        e.setAmount(800.0);
        e.setCurrencyCode("GBP");
        e.setDate(LocalDate.of(2026, 5, 15));
        when(manualExpenseRepository.findById(17L)).thenReturn(Optional.of(e));

        ConvertedMoneyDto dto = service.convertManualExpense(17L, "eur");

        assertThat(dto.getOriginalCurrency()).isEqualTo("GBP");
        assertThat(dto.getTargetCurrency()).isEqualTo("EUR");
        assertThat(dto.getPeriod()).isEqualTo("2026-05");
        assertThat(dto.getConvertedAmount()).isCloseTo(1000.0, within(1e-6));
    }

    // C ── record currencyCode null → falls back to the site currency
    @Test
    void fallsBackToSiteCurrencyWhenRecordCurrencyIsNull() {
        storeEurRate(2026, 5, "TND", 3.40);
        BudgetMonthly b = new BudgetMonthly();
        b.setId(11L);
        b.setAmount(3400.0);
        b.setCurrencyCode(null);
        b.setSite(site("TND"));
        b.setYear(2026);
        b.setMonth(5);
        when(budgetMonthlyRepository.findById(11L)).thenReturn(Optional.of(b));

        ConvertedMoneyDto dto = service.convertBudget(11L, "EUR");

        assertThat(dto.getOriginalCurrency()).isEqualTo("TND");
        assertThat(dto.getConvertedAmount()).isCloseTo(1000.0, within(1e-6));
    }

    // D ── Incident cost conversion
    @Test
    void convertsIncidentCostRealUsingSiteCurrencyAndIncidentDate() {
        storeEurRate(2026, 5, "TND", 3.40);
        Incident i = new Incident();
        i.setCostReal(3400.0);
        i.setSites(site("TND"));
        i.setDate(date(2026, 5, 12));
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(i));

        ConvertedMoneyDto dto = service.convertIncidentCost(5L, "EUR", "real");

        assertThat(dto.getRecordType()).isEqualTo("INCIDENT");
        assertThat(dto.getSourceField()).isEqualTo("costReal");
        assertThat(dto.getOriginalCurrency()).isEqualTo("TND");
        assertThat(dto.getPeriod()).isEqualTo("2026-05");
        assertThat(dto.getConvertedAmount()).isCloseTo(1000.0, within(1e-6));
    }

    // E ── Maintenance cost conversion (currency via Equipment -> Site)
    @Test
    void convertsMaintenanceCostUsingEquipmentSiteCurrency() {
        storeEurRate(2026, 5, "GBP", 0.80);
        Equipement eq = new Equipement();
        eq.setSite(site("GBP"));
        Maintenance m = new Maintenance();
        m.setCostReal(800.0);
        m.setEquipement(eq);
        m.setDate(date(2026, 5, 4));
        when(maintenanceRepository.findById(7L)).thenReturn(Optional.of(m));

        ConvertedMoneyDto dto = service.convertMaintenanceCost(7L, "EUR");

        assertThat(dto.getRecordType()).isEqualTo("MAINTENANCE");
        assertThat(dto.getOriginalCurrency()).isEqualTo("GBP");
        assertThat(dto.getConvertedAmount()).isCloseTo(1000.0, within(1e-6));
    }

    // F ── cross conversion X -> Y (via EUR)
    @Test
    void crossConvertsStoredRecordThroughEur() {
        storeEurRate(2026, 5, "TND", 3.40);
        storeEurRate(2026, 5, "GBP", 0.80);
        BudgetMonthly b = new BudgetMonthly();
        b.setId(12L);
        b.setAmount(3400.0);
        b.setCurrencyCode("TND");
        b.setYear(2026);
        b.setMonth(5);
        when(budgetMonthlyRepository.findById(12L)).thenReturn(Optional.of(b));

        ConvertedMoneyDto dto = service.convertBudget(12L, "GBP");

        assertThat(dto.getConvertedAmount()).isCloseTo(800.0, within(1e-6)); // 3400 TND -> 1000 EUR -> 800 GBP
    }

    // G ── same currency
    @Test
    void sameCurrencyReturnsOriginalAmount() {
        BudgetMonthly b = new BudgetMonthly();
        b.setId(13L);
        b.setAmount(5000.0);
        b.setCurrencyCode("TND");
        b.setYear(2026);
        b.setMonth(5);
        when(budgetMonthlyRepository.findById(13L)).thenReturn(Optional.of(b));

        ConvertedMoneyDto dto = service.convertBudget(13L, "TND");

        assertThat(dto.isRateAvailable()).isTrue();
        assertThat(dto.getConvertedAmount()).isEqualTo(5000.0);
        assertThat(dto.getRateUsed()).isEqualTo(1.0);
    }

    // H ── missing FX rate → unavailable, never the raw amount as the target currency
    @Test
    void missingRateYieldsUnavailableNotAFabricatedValue() {
        BudgetMonthly b = new BudgetMonthly();
        b.setId(14L);
        b.setAmount(5000.0);
        b.setCurrencyCode("TND");
        b.setYear(2026);
        b.setMonth(5);
        when(budgetMonthlyRepository.findById(14L)).thenReturn(Optional.of(b));
        // no EUR->TND rate stubbed

        ConvertedMoneyDto dto = service.convertBudget(14L, "EUR");

        assertThat(dto.isRateAvailable()).isFalse();
        assertThat(dto.getConvertedAmount()).isNull();
        assertThat(dto.getOriginalAmount()).isEqualTo(5000.0);
        assertThat(dto.getOriginalCurrency()).isEqualTo("TND");
        assertThat(dto.getUnavailableReason()).contains("missing FX rate", "TND");
    }

    // I ── missing site / currency → explicit failure, never assume reporting currency
    @Test
    void incidentWithoutSiteIsUnavailable() {
        Incident i = new Incident();
        i.setCostReal(1000.0);
        i.setSites(null);
        i.setDate(date(2026, 5, 1));
        when(incidentRepository.findById(9L)).thenReturn(Optional.of(i));

        ConvertedMoneyDto dto = service.convertIncidentCost(9L, "EUR", "real");

        assertThat(dto.isRateAvailable()).isFalse();
        assertThat(dto.getConvertedAmount()).isNull();
        assertThat(dto.getUnavailableReason()).contains("source currency is unknown");
    }

    @Test
    void maintenanceWithoutEquipmentSiteIsUnavailable() {
        Maintenance m = new Maintenance();
        m.setCostReal(1000.0);
        m.setEquipement(null);
        m.setDate(date(2026, 5, 1));
        when(maintenanceRepository.findById(3L)).thenReturn(Optional.of(m));

        ConvertedMoneyDto dto = service.convertMaintenanceCost(3L, "EUR");

        assertThat(dto.isRateAvailable()).isFalse();
        assertThat(dto.getUnavailableReason()).contains("source currency is unknown");
    }

    @Test
    void unknownRecordIsNotFound() {
        when(budgetMonthlyRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.convertBudget(999L, "EUR"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void invalidTargetCurrencyIsUnavailable() {
        BudgetMonthly b = new BudgetMonthly();
        b.setId(15L);
        b.setAmount(100.0);
        b.setCurrencyCode("EUR");
        b.setYear(2026);
        b.setMonth(5);
        when(budgetMonthlyRepository.findById(15L)).thenReturn(Optional.of(b));

        ConvertedMoneyDto dto = service.convertBudget(15L, "EURO");

        assertThat(dto.isRateAvailable()).isFalse();
        assertThat(dto.getUnavailableReason()).contains("target currency");
    }

    @Test
    void supportedTargetCurrenciesMergesFxCodesAndReportingCurrency() {
        when(fxRateRepository.findDistinctFromCurrencyCodes()).thenReturn(List.of("EUR"));
        when(fxRateRepository.findDistinctToCurrencyCodes()).thenReturn(List.of("gbp", "TND", "GBP"));

        assertThat(service.supportedTargetCurrencies()).containsExactly("EUR", "GBP", "TND");
    }
}
