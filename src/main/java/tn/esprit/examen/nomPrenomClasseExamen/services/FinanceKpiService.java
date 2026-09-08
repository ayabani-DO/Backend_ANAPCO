package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.dto.FinanceKpiDto;

/**
 * LEGACY finance-KPI stack ({@code GET /api/finance/kpi/get}). This is a <b>duplicate</b> of the
 * canonical {@link FinancialAnalyticsService} kept only for backward compatibility of the old DTO
 * shape ({@link FinanceKpiDto}).
 *
 * <p>Since 1B it holds <b>no independent formula</b> — it is a thin adapter that maps the canonical
 * {@link FinancialKpiDTO} onto the legacy field names:
 * <pre>
 *   realEur       ← totalRealCost   (was manual-expenses-only before 1B)
 *   varianceEur   ← budgetVariance  (totalRealCost - budget)
 *   variancePercent ← budgetVariancePercent  (null when budget == 0)
 *   forecastEur   ← forecastNextMonth (next-month budget; a real forecast is Step 2)
 * </pre>
 *
 * @deprecated use {@link FinancialAnalyticsService} / {@code GET /api/analytics/financial}.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class FinanceKpiService {

    private final FinancialAnalyticsService financialAnalyticsService;

    public FinanceKpiDto getKpi(Long siteId, Integer year, Integer month) {
        FinancialKpiDTO k = financialAnalyticsService.getFinancialKpi(siteId, year, month);

        double budget = k.getBudget();
        double real = k.getTotalRealCost();
        double variance = k.getBudgetVariance();
        Double variancePercent = k.getBudgetVariancePercent();
        double forecast = k.getForecastNextMonth();
        double varianceForecast = round2(real - forecast);
        Double varianceForecastPercent = forecast == 0d ? null : round2(varianceForecast / forecast * 100d);

        return FinanceKpiDto.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .budgetEur(round2(budget))
                .realEur(round2(real))
                .varianceEur(round2(variance))
                .variancePercent(variancePercent)
                .forecastEur(round2(forecast))
                .varianceForecastEur(varianceForecast)
                .varianceForecastPercent(varianceForecastPercent)
                .riskLevel(riskLevel(variancePercent))
                .build();
    }

    /** LOW/MEDIUM/HIGH by |variance %|; UNKNOWN when the percentage is undefined (zero budget). */
    private String riskLevel(Double variancePercent) {
        if (variancePercent == null) {
            return "UNKNOWN";
        }
        double abs = Math.abs(variancePercent);
        if (abs < 5d) {
            return "LOW";
        }
        if (abs < 15d) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
