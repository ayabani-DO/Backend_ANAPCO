package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.CurrencyConverter;
import tn.esprit.examen.nomPrenomClasseExamen.dto.ConvertedMoneyDto;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Single service for <b>record-scoped, display-only</b> currency conversion of a stored monetary
 * record (Budget / ManualExpense amount, Incident / Maintenance cost).
 *
 * <p>Given only a record id and a target currency, it derives the amount, the source currency
 * (record currency → site currency, never an assumed default) and the record's own business month,
 * then delegates to {@link CurrencyConverter#convert(Double, String, String, int, int)} — the same
 * Step 1A arithmetic used by the analytics layer. Nothing is persisted; a missing rate / missing
 * currency / missing date yields an explicit {@code rateAvailable = false} result, never a fabricated
 * value.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordConversionService {

    private final BudgetMonthlyRepository budgetMonthlyRepository;
    private final ManualExpenseRepository manualExpenseRepository;
    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final FxRateRepository fxRateRepository;
    private final CurrencyConverter currencyConverter;

    // ── Supported target currencies (TEMPORARY) ──────────────────────────────
    // Derived from distinct codes in the FxRate table + the reporting currency. Step 1C-B replaces
    // this with GET /api/finance/currencies backed by the Currency reference table.
    public List<String> supportedTargetCurrencies() {
        Set<String> codes = new TreeSet<>();
        codes.add(currencyConverter.reportingCurrency());
        fxRateRepository.findDistinctFromCurrencyCodes().forEach(c -> addCode(codes, c));
        fxRateRepository.findDistinctToCurrencyCodes().forEach(c -> addCode(codes, c));
        return new ArrayList<>(codes);
    }

    // ── Record-scoped conversions ───────────────────────────────────────────

    public ConvertedMoneyDto convertBudget(Long id, String targetCurrency) {
        BudgetMonthly b = budgetMonthlyRepository.findById(id)
                .orElseThrow(() -> notFound("BudgetMonthly", id));
        String source = firstNonBlank(b.getCurrencyCode(), siteCurrency(b.getSite()));
        return build("BUDGET_MONTHLY", id, null, b.getAmount(), source, b.getYear(), b.getMonth(), targetCurrency);
    }

    public ConvertedMoneyDto convertManualExpense(Long id, String targetCurrency) {
        ManualExpense e = manualExpenseRepository.findById(id)
                .orElseThrow(() -> notFound("ManualExpense", id));
        String source = firstNonBlank(e.getCurrencyCode(), siteCurrency(e.getSite()));
        LocalDate d = e.getDate();
        Integer year = d != null ? d.getYear() : null;
        Integer month = d != null ? d.getMonthValue() : null;
        return build("MANUAL_EXPENSE", id, null, e.getAmount(), source, year, month, targetCurrency);
    }

    public ConvertedMoneyDto convertIncidentCost(Long id, String targetCurrency, String field) {
        Incident i = incidentRepository.findById(id)
                .orElseThrow(() -> notFound("Incident", id));
        String resolvedField = normaliseIncidentField(field);
        Double amount = "costEstimated".equals(resolvedField) ? i.getCostEstimated() : i.getCostReal();
        // Incident inherits its currency from its Site — there is no Incident.currencyCode.
        String source = i.getSites() != null ? i.getSites().getCurrencyCode() : null;
        YearMonth ym = i.getDate() != null ? toYearMonth(i.getDate()) : null;
        return build("INCIDENT", id, resolvedField, amount, source,
                ym != null ? ym.getYear() : null, ym != null ? ym.getMonthValue() : null, targetCurrency);
    }

    public ConvertedMoneyDto convertMaintenanceCost(Long id, String targetCurrency) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> notFound("Maintenance", id));
        // Maintenance inherits its currency from Equipment -> Site — there is no Maintenance.currencyCode
        // and no Maintenance -> Site link.
        Sites site = m.getEquipement() != null ? m.getEquipement().getSite() : null;
        String source = site != null ? site.getCurrencyCode() : null;
        YearMonth ym = m.getDate() != null ? toYearMonth(m.getDate()) : null;
        return build("MAINTENANCE", id, "costReal", m.getCostReal(), source,
                ym != null ? ym.getYear() : null, ym != null ? ym.getMonthValue() : null, targetCurrency);
    }

    // ── Core builder ────────────────────────────────────────────────────────

    private ConvertedMoneyDto build(String recordType, Long recordId, String sourceField,
                                    Double amount, String sourceCurrency,
                                    Integer year, Integer month, String targetCurrencyRaw) {
        String target = normaliseCode(targetCurrencyRaw);
        String source = normaliseCode(sourceCurrency);
        String period = (year != null && month != null) ? String.format("%d-%02d", year, month) : null;

        ConvertedMoneyDto.ConvertedMoneyDtoBuilder dto = ConvertedMoneyDto.builder()
                .recordType(recordType)
                .recordId(recordId)
                .sourceField(sourceField)
                .originalAmount(amount)
                .originalCurrency(source)
                .targetCurrency(target)
                .businessYear(year)
                .businessMonth(month)
                .period(period);

        // ── Explicit unavailable cases — never assume a currency ──
        if (target == null || !target.matches("[A-Z]{3}")) {
            return unavailable(dto, "invalid or missing target currency: " + targetCurrencyRaw);
        }
        if (amount == null) {
            return unavailable(dto, "record has no " + (sourceField != null ? sourceField : "amount") + " value");
        }
        if (source == null) {
            return unavailable(dto, "source currency is unknown (record has no currency and its site has none)");
        }
        if (year == null || month == null) {
            return unavailable(dto, "record has no business period (year/month)");
        }

        CurrencyConverter.ConversionResult r = currencyConverter.convert(amount, source, target, year, month);
        if (!r.converted()) {
            return unavailable(dto, r.reason());
        }

        double converted = r.convertedAmount();
        Double rateUsed = amount != 0d ? round6(converted / amount) : null;
        return dto
                .convertedAmount(round2(converted))
                .rateAvailable(true)
                .rateUsed(rateUsed)
                .ratePeriodUsed(period)
                .build();
    }

    private ConvertedMoneyDto unavailable(ConvertedMoneyDto.ConvertedMoneyDtoBuilder dto, String reason) {
        return dto.convertedAmount(null).rateAvailable(false).rateUsed(null).ratePeriodUsed(null)
                .unavailableReason(reason).build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void addCode(Set<String> set, String code) {
        if (code != null && !code.isBlank()) {
            set.add(code.trim().toUpperCase(Locale.ROOT));
        }
    }

    private static String siteCurrency(Sites site) {
        return site != null ? site.getCurrencyCode() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }

    private static String normaliseCode(String code) {
        return (code == null || code.isBlank()) ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String normaliseIncidentField(String field) {
        if (field == null) return "costReal";
        String f = field.trim().toLowerCase(Locale.ROOT);
        return (f.equals("estimated") || f.equals("costestimated") || f.equals("cost_estimated"))
                ? "costEstimated" : "costReal";
    }

    private static YearMonth toYearMonth(Date date) {
        return YearMonth.from(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private static ResponseStatusException notFound(String type, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " not found with id: " + id);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
