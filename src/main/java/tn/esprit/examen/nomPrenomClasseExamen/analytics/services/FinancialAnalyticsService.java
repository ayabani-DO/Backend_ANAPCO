package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics Layer — single source of truth for a site's financial health (budget vs. real spend).
 *
 * <p>Centralises the budget / real / variance / FX-to-EUR logic previously duplicated between
 * {@code FinanceKpiService} and {@code MonthlyFeatureAggregationService}, and adds the year-level
 * cost trend and category breakdown needed by dashboards.
 *
 * <p>Monetary formulas mirror {@code FinanceKpiService} so figures stay consistent with the legacy
 * {@code /api/finance/kpi/get} endpoint. One difference by design: FX conversion here is lenient
 * (missing rate → raw amount + warning) so a read-only analytics call never fails hard, whereas the
 * legacy service throws. The legacy behaviour is untouched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FinancialAnalyticsService {

    private static final String BASE_CURRENCY = "EUR";

    private final SitesRepository sitesRepository;
    private final BudgetMonthlyRepository budgetMonthlyRepository;
    private final ManualExpenseRepository manualExpenseRepository;
    private final CurrencyConverter currencyConverter;

    public FinancialKpiDTO getFinancialKpi(Long siteId, int year, int month) {
        Sites site = sitesRepository.findById(siteId)
                .orElseThrow(() -> new RuntimeException("Site not found"));
        String siteCurrency = site.getCurrencyCode();

        // ── Budget for the requested month ──────────────────────
        BudgetMonthly budget = budgetMonthlyRepository
                .findBySite_IdSiteAndYearAndMonth(siteId, year, month).orElse(null);
        double budgetEur = budget == null ? 0d
                : currencyConverter.toEurOrRaw(year, month, budget.getAmount(), budget.getCurrencyCode(), siteCurrency);

        // ── One query for the whole year, reused for the month figure, trend and categories ──
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<ManualExpense> yearExpenses =
                manualExpenseRepository.findBySite_IdSiteAndDateBetween(siteId, yearStart, yearEnd);

        // ── Real spend for the requested month ──────────────────
        double realEur = yearExpenses.stream()
                .filter(e -> e.getDate() != null && e.getDate().getMonthValue() == month)
                .mapToDouble(e -> currencyConverter.toEurOrRaw(e.getDate().getYear(), e.getDate().getMonthValue(),
                        e.getAmount(), e.getCurrencyCode(), siteCurrency))
                .sum();

        double varianceEur = realEur - budgetEur;
        double variancePercent = budgetEur == 0d ? 0d : (varianceEur / budgetEur) * 100d;

        // ── Forecast next month (next-month budget, else current budget) ──
        LocalDate next = LocalDate.of(year, month, 1).plusMonths(1);
        BudgetMonthly nextBudget = budgetMonthlyRepository
                .findBySite_IdSiteAndYearAndMonth(siteId, next.getYear(), next.getMonthValue()).orElse(null);
        double forecastNextMonth = nextBudget == null ? budgetEur
                : currencyConverter.toEurOrRaw(next.getYear(), next.getMonthValue(),
                nextBudget.getAmount(), nextBudget.getCurrencyCode(), siteCurrency);

        return FinancialKpiDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .budget(round2(budgetEur))
                .real(round2(realEur))
                .variance(round2(varianceEur))
                .variancePercent(round2(variancePercent))
                .forecastNextMonth(round2(forecastNextMonth))
                .currency(BASE_CURRENCY)
                .costTrend(buildCostTrend(yearExpenses, siteCurrency, year))
                .topExpenseCategories(buildTopCategories(yearExpenses, siteCurrency))
                .build();
    }

    // ── Trend & breakdown ────────────────────────────────────

    private List<FinancialKpiDTO.MonthlyCost> buildCostTrend(List<ManualExpense> yearExpenses,
                                                             String siteCurrency, int year) {
        double[] perMonth = new double[13]; // index 1..12
        for (ManualExpense e : yearExpenses) {
            if (e.getDate() == null) continue;
            int m = e.getDate().getMonthValue();
            perMonth[m] += currencyConverter.toEurOrRaw(e.getDate().getYear(), m, e.getAmount(), e.getCurrencyCode(), siteCurrency);
        }
        List<FinancialKpiDTO.MonthlyCost> trend = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            trend.add(new FinancialKpiDTO.MonthlyCost(String.format("%d-%02d", year, m), round2(perMonth[m])));
        }
        return trend;
    }

    private List<FinancialKpiDTO.CategoryExpense> buildTopCategories(List<ManualExpense> yearExpenses,
                                                                     String siteCurrency) {
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (ManualExpense e : yearExpenses) {
            if (e.getDate() == null) continue;
            String category = e.getCategory() != null ? e.getCategory().name() : "UNKNOWN";
            double eur = currencyConverter.toEurOrRaw(e.getDate().getYear(), e.getDate().getMonthValue(),
                    e.getAmount(), e.getCurrencyCode(), siteCurrency);
            byCategory.merge(category, eur, Double::sum);
        }
        return byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> new FinancialKpiDTO.CategoryExpense(entry.getKey(), round2(entry.getValue())))
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────

    private double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
