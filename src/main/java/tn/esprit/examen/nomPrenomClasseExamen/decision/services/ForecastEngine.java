package tn.esprit.examen.nomPrenomClasseExamen.decision.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.ForecastDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Decision Layer — short-term cost forecast. It receives the financial and operational KPIs from the
 * Analytics Layer and projects them; it does not recompute any cost or budget.
 */
@Service
@RequiredArgsConstructor
public class ForecastEngine {

    private final OperationalAnalyticsService operationalAnalytics;
    private final FinancialAnalyticsService financialAnalytics;

    public ForecastDTO forecast(Long siteId, int year, int month) {
        return forecast(siteId, year, month,
                financialAnalytics.getFinancialKpi(siteId, year, month),
                operationalAnalytics.getOperationalKpi(siteId, year, month));
    }

    /**
     * Overload for orchestrators that already hold the analytics KPIs, so they are not recomputed.
     */
    public ForecastDTO forecast(Long siteId, int year, int month,
                                FinancialKpiDTO financial, OperationalKpiDTO operational) {
        List<FinancialKpiDTO.MonthlyCost> trend = financial.getCostTrend();
        double current = monthValue(trend, month);
        double previous = month > 1 ? monthValue(trend, month - 1) : 0.0;

        double trendPercent = previous > 0 ? round2((current - previous) / previous * 100.0) : 0.0;
        String trendDirection = previous <= 0 ? "STABLE"
                : trendPercent > 10 ? "RISING"
                : trendPercent < -10 ? "DECLINING"
                : "STABLE";

        long monthsWithData = trend.stream().filter(m -> m.amountEur() > 0).count();
        String confidence = monthsWithData >= 6 ? "HIGH" : monthsWithData >= 3 ? "MEDIUM" : "LOW";

        List<String> drivers = new ArrayList<>();
        if ("RISING".equals(trendDirection)) {
            drivers.add("Rising monthly cost trend (" + trendPercent + "%)");
        }
        if (operational.getCriticalIncidentCount() > 0) {
            drivers.add("Recent critical incidents (" + operational.getCriticalIncidentCount() + ")");
        }
        if (Math.abs(financial.getVariancePercent()) > 15) {
            drivers.add("Significant budget variance (" + financial.getVariancePercent() + "%)");
        }
        if (drivers.isEmpty()) {
            drivers.add("Stable cost profile");
        }

        return ForecastDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .nextMonthCostForecast(financial.getForecastNextMonth())
                .trendDirection(trendDirection)
                .trendPercent(trendPercent)
                .confidence(confidence)
                .drivers(drivers)
                .build();
    }

    /** Reads the cost trend for a given month (1..12); the trend is an ordered 12-point series. */
    private double monthValue(List<FinancialKpiDTO.MonthlyCost> trend, int month) {
        if (trend == null || month < 1 || month > trend.size()) return 0.0;
        return trend.get(month - 1).amountEur();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
