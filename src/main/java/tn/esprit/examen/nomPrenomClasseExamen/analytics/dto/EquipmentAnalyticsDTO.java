package tn.esprit.examen.nomPrenomClasseExamen.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Consolidated health card for a single piece of equipment, assembled by the Analytics Layer
 * ({@code EquipmentAnalyticsService}) from the operational primitives and the RUL engine — it does
 * not re-implement any calculation.
 */
@Data
@Builder
public class EquipmentAnalyticsDTO {

    private Long equipmentId;
    private String equipmentName;
    private String siteName;
    /** Site-local currency of the cost figures below (per-transaction FX normalisation is deferred). */
    private String currency;

    /** 0..100, higher is healthier ({@code 100 - rulScore}). */
    private double healthScore;

    // ── Canonical all-time cost breakdown ────────────────────
    private double incidentRealCost;
    private double realisedMaintenanceCost;
    /** incidentRealCost + realisedMaintenanceCost. Planned maintenance is NOT included. */
    private double operationalCost;
    /** Future/committed = upcoming planned + in-progress maintenance. Reported separately. */
    private double plannedMaintenanceCost;

    /** @deprecated use {@link #operationalCost} — identical value (was incident + realised + planned before 1B). */
    @Deprecated
    private double totalCost;

    private IncidentSummary incidentSummary;
    private MaintenanceSummary maintenanceSummary;

    private int rulScore;
    private int remainingDays;
    private String rulAction;

    private String riskLevel;

    /** @deprecated use {@link #plannedMaintenanceCost}. This is committed maintenance spend, not a forecast. */
    @Deprecated
    private double forecastCost;

    public record IncidentSummary(long count, LocalDate lastDate, double avgSeverity) {
    }

    public record MaintenanceSummary(long preventive, long corrective, long inspection, LocalDate nextPlanned) {
    }
}
