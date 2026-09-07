package tn.esprit.examen.nomPrenomClasseExamen.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Consolidated financial health of a site, produced by the Analytics Layer
 * ({@code FinancialAnalyticsService}). All monetary values are normalised to EUR.
 *
 * <p>{@code budget}, {@code real}, {@code variance}, {@code variancePercent} and
 * {@code forecastNextMonth} concern the requested (year, month) — mirroring the legacy
 * {@code FinanceKpiService}. {@code costTrend} and {@code topExpenseCategories} give the
 * surrounding year-level context for dashboards.
 */
@Data
@Builder
public class FinancialKpiDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    private double budget;
    private double real;
    private double variance;
    private double variancePercent;

    /** Rule-based forecast for the next month (next-month budget in EUR, else current budget). */
    private double forecastNextMonth;

    private String currency;

    /** Month-by-month realised spend across the requested year. */
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
