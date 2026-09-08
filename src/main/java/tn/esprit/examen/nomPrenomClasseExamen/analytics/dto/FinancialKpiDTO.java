package tn.esprit.examen.nomPrenomClasseExamen.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Consolidated financial health of a site, produced by the Analytics Layer
 * ({@code FinancialAnalyticsService}). All monetary values are normalised to {@link #currency}
 * (reporting currency, default EUR).
 *
 * <p>Canonical model (frontend should read these directly — never recompute):
 * <pre>
 *   operationalCost  = incidentRealCost + realisedMaintenanceCost      (from the Operational layer)
 *   totalRealCost    = operationalCost + manualExpenses
 *   budgetVariance   = totalRealCost - budget                          (+ = over, - = under)
 *   budgetVariancePercent = budget != 0 ? variance / budget * 100 : null
 * </pre>
 * {@link #fxComplete} / {@link #fxUnavailable} disclose any line that could not be converted.
 */
@Data
@Builder
public class FinancialKpiDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    /** Reporting currency all monetary values are expressed in. */
    private String currency;

    // ── Canonical cost breakdown ─────────────────────────────
    private double incidentRealCost;
    private double realisedMaintenanceCost;
    /** Future/committed maintenance — reported for context, NOT part of totalRealCost. */
    private double plannedMaintenanceCost;
    private double manualExpenses;
    /** incidentRealCost + realisedMaintenanceCost. */
    private double operationalCost;
    /** operationalCost + manualExpenses — the canonical "money already spent". */
    private double totalRealCost;

    // ── Budget vs. real ──────────────────────────────────────
    private double budget;
    /** totalRealCost - budget. Positive = over budget. */
    private double budgetVariance;
    /** (budgetVariance / budget) * 100, or {@code null} when budget == 0 (never 0%). */
    private Double budgetVariancePercent;

    // ── FX disclosure ────────────────────────────────────────
    private boolean fxComplete;
    private List<FxGap> fxUnavailable;

    // ── Legacy fields (kept for existing consumers: RiskEngine, dashboard, chatbot) ──
    /** @deprecated use {@link #totalRealCost}. Was manual-expenses-only before 1B; now equals totalRealCost. */
    @Deprecated
    private double real;
    /** @deprecated use {@link #budgetVariance}. Was manual-real minus budget before 1B. */
    @Deprecated
    private double variance;
    /** @deprecated use {@link #budgetVariancePercent} (nullable). This stays 0.0 when budget == 0. */
    @Deprecated
    private double variancePercent;

    /**
     * Next-month budget in the reporting currency (falls back to the current month's budget).
     *
     * @deprecated this is a <b>budget</b> figure, not a cost forecast. A real predicted-cost forecast
     * is Forecast Step 2; until then treat this as "next-month budget".
     */
    @Deprecated
    private double forecastNextMonth;

    /** Month-by-month realised spend across the requested year (manual expenses only for now — see class doc). */
    private List<MonthlyCost> costTrend;

    /** Expense categories for the requested year, highest spend first. */
    private List<CategoryExpense> topExpenseCategories;

    /** One point of the year-level cost trend. {@code month} is formatted "yyyy-MM". */
    public record MonthlyCost(String month, double amountEur) {
    }

    /** Total realised spend for one expense category over the requested year. */
    public record CategoryExpense(String category, double amountEur) {
    }
}
