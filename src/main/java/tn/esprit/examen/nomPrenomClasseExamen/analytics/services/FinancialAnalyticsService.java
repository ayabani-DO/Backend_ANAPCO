package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FxGap;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalCostBreakdown;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics Layer — canonical owner of a site's financial health.
 *
 * <p>Owns: {@code budget}, {@code manualExpenses}, {@code totalRealCost}, {@code budgetVariance},
 * {@code budgetVariancePercent}, the financial cost trend and category breakdown.
 * It consumes the operational cost subtotal from {@code OperationalAnalyticsService} — it does
 * <b>not</b> re-aggregate incidents or maintenance.
 *
 * <pre>
 *   totalRealCost         = operationalCost (incident real + realised maintenance) + manualExpenses
 *   budgetVariance        = totalRealCost - budget
 *   budgetVariancePercent = budget != 0 ? variance / budget * 100 : null
 * </pre>
 *
 * <p>All amounts are normalised to the reporting currency, each at the record's own business month.
 * A line whose FX rate is missing is excluded and disclosed via {@code fxUnavailable}
 * ({@code fxComplete = false}); the endpoint never fails and never uses a raw local amount.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FinancialAnalyticsService {

    private final SitesRepository sitesRepository;
    private final BudgetMonthlyRepository budgetMonthlyRepository;
    private final ManualExpenseRepository manualExpenseRepository;
    private final CurrencyConverter currencyConverter;
    private final OperationalAnalyticsService operationalAnalytics;

    public FinancialKpiDTO getFinancialKpi(Long siteId, int year, int month) {
        Sites site = sitesRepository.findById(siteId)
                .orElseThrow(() -> new RuntimeException("Site not found"));
        String siteCurrency = site.getCurrencyCode();
        String reporting = currencyConverter.reportingCurrency();

        // ── Operational cost subtotal (canonical, already normalised) ──
        OperationalCostBreakdown op = operationalAnalytics.operationalCost(siteId, year, month);

        // ── Budget for the requested month (converted at that month) ──
        BudgetMonthly budget = budgetMonthlyRepository
                .findBySite_IdSiteAndYearAndMonth(siteId, year, month).orElse(null);
        ReportingAmount budgetAmt = new ReportingAmount();
        if (budget != null) {
            budgetAmt.add(currencyConverter.convert(budget.getAmount(),
                    effectiveCurrency(budget.getCurrencyCode(), siteCurrency), year, month), year, month);
        }
        double budgetEur = budgetAmt.total();

        // ── One query for the whole year: month figure, trend and categories ──
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        List<ManualExpense> yearExpenses =
                manualExpenseRepository.findBySite_IdSiteAndDateBetween(siteId, yearStart, yearEnd);

        // Manual expenses for the requested month (each converted at its own month).
        ReportingAmount manualMonth = new ReportingAmount();
        for (ManualExpense e : yearExpenses) {
            if (e.getDate() != null && e.getDate().getMonthValue() == month && e.getDate().getYear() == year) {
                manualMonth.add(currencyConverter.convert(e.getAmount(),
                        effectiveCurrency(e.getCurrencyCode(), siteCurrency),
                        e.getDate().getYear(), e.getDate().getMonthValue()), e.getDate().getYear(), e.getDate().getMonthValue());
            }
        }
        double manualEur = manualMonth.total();

        // ── Canonical totals ──
        double totalRealCost = round2(op.operationalCost() + manualEur);
        double budgetVariance = round2(totalRealCost - budgetEur);
        Double budgetVariancePercent = budgetEur == 0d ? null : round2(budgetVariance / budgetEur * 100d);

        // ── Forecast (next-month budget — NOT a real forecast; see DTO javadoc) ──
        LocalDate next = LocalDate.of(year, month, 1).plusMonths(1);
        BudgetMonthly nextBudget = budgetMonthlyRepository
                .findBySite_IdSiteAndYearAndMonth(siteId, next.getYear(), next.getMonthValue()).orElse(null);
        ReportingAmount forecastAmt = new ReportingAmount();
        if (nextBudget != null) {
            forecastAmt.add(currencyConverter.convert(nextBudget.getAmount(),
                    effectiveCurrency(nextBudget.getCurrencyCode(), siteCurrency),
                    next.getYear(), next.getMonthValue()), next.getYear(), next.getMonthValue());
        }
        double forecastNextMonth = nextBudget == null ? budgetEur : forecastAmt.total();

        // ── Year-level trend (totalRealCost per month) + categories ──
        TrendResult trend = buildTrend(siteId, year, siteCurrency, yearExpenses);

        // ── FX completeness across every line that fed the month figures ──
        List<FxGap> gaps = new ArrayList<>();
        gaps.addAll(op.fxUnavailable());
        gaps.addAll(budgetAmt.gaps());
        gaps.addAll(manualMonth.gaps());
        gaps.addAll(forecastAmt.gaps());
        gaps.addAll(trend.gaps());
        boolean fxComplete = op.fxComplete() && budgetAmt.complete() && manualMonth.complete()
                && forecastAmt.complete() && trend.complete();

        // ── Legacy variancePercent stays a primitive: 0.0 on zero budget (RiskEngine depends on it) ──
        double legacyVariancePercent = budgetEur == 0d ? 0d : round2(budgetVariance / budgetEur * 100d);

        return FinancialKpiDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .currency(reporting)
                .incidentRealCost(op.incidentRealCost())
                .realisedMaintenanceCost(op.realisedMaintenanceCost())
                .plannedMaintenanceCost(op.plannedMaintenanceCost())
                .manualExpenses(manualEur)
                .operationalCost(op.operationalCost())
                .totalRealCost(totalRealCost)
                .budget(budgetEur)
                .budgetVariance(budgetVariance)
                .budgetVariancePercent(budgetVariancePercent)
                .fxComplete(fxComplete)
                .fxUnavailable(gaps)
                // ── legacy ──
                .real(totalRealCost)
                .variance(budgetVariance)
                .variancePercent(legacyVariancePercent)
                .forecastNextMonth(round2(forecastNextMonth))
                .costTrend(trend.points())
                .topExpenseCategories(trend.categories())
                .build();
    }

    // ── Trend & breakdown ────────────────────────────────────

    private TrendResult buildTrend(Long siteId, int year, String siteCurrency, List<ManualExpense> yearExpenses) {
        ReportingAmount acc = new ReportingAmount();

        // Manual expenses bucketed by month (converted at each expense's own month).
        double[] manualByMonth = new double[13];
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (ManualExpense e : yearExpenses) {
            if (e.getDate() == null) continue;
            int m = e.getDate().getMonthValue();
            CurrencyConverter.ConversionResult r = currencyConverter.convert(e.getAmount(),
                    effectiveCurrency(e.getCurrencyCode(), siteCurrency), e.getDate().getYear(), m);
            acc.add(r, e.getDate().getYear(), m);
            if (r.converted()) {
                manualByMonth[m] += r.convertedAmount();
                String category = e.getCategory() != null ? e.getCategory().name() : "UNKNOWN";
                byCategory.merge(category, r.convertedAmount(), Double::sum);
            }
        }

        // Operational cost (site-local) bucketed by month, converted at each month.
        double[] opRawByMonth = operationalAnalytics.rawOperationalCostByMonth(siteId, year);

        List<FinancialKpiDTO.MonthlyCost> points = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            CurrencyConverter.ConversionResult opR = currencyConverter.convert(opRawByMonth[m], siteCurrency, year, m);
            acc.add(opR, year, m);
            double opConverted = opR.converted() ? opR.convertedAmount() : 0d;
            points.add(new FinancialKpiDTO.MonthlyCost(String.format("%d-%02d", year, m),
                    round2(opConverted + manualByMonth[m])));
        }

        List<FinancialKpiDTO.CategoryExpense> categories = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> new FinancialKpiDTO.CategoryExpense(entry.getKey(), round2(entry.getValue())))
                .toList();

        return new TrendResult(points, categories, acc.complete(), acc.gaps());
    }

    private record TrendResult(List<FinancialKpiDTO.MonthlyCost> points,
                               List<FinancialKpiDTO.CategoryExpense> categories,
                               boolean complete,
                               List<FxGap> gaps) {
    }

    // ── Helpers ──────────────────────────────────────────────

    /** record currencyCode → site currency (no implicit reporting-currency fallback). */
    private static String effectiveCurrency(String currencyCode, String siteCurrency) {
        return (currencyCode == null || currencyCode.isBlank()) ? siteCurrency : currencyCode;
    }

    private double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
