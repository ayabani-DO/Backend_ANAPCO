package tn.esprit.examen.nomPrenomClasseExamen.analytics.dto;

import java.util.List;

/**
 * Canonical operational cost subtotal for a site/month, normalised to the reporting currency.
 *
 * <p>Produced by {@code OperationalAnalyticsService} (the single owner of incident + maintenance
 * aggregation) and consumed by {@code FinancialAnalyticsService} to build {@code totalRealCost}
 * without re-summing incidents or maintenance.
 *
 * <ul>
 *   <li>{@code operationalCost = incidentRealCost + realisedMaintenanceCost}</li>
 *   <li>{@code plannedMaintenanceCost} is future/committed spend — reported separately, never part
 *       of {@code operationalCost}</li>
 * </ul>
 */
public record OperationalCostBreakdown(double incidentRealCost,
                                       double realisedMaintenanceCost,
                                       double plannedMaintenanceCost,
                                       double operationalCost,
                                       String currency,
                                       boolean fxComplete,
                                       List<FxGap> fxUnavailable) {
}
