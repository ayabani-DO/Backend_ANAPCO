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

    /** 0..100, higher is healthier ({@code 100 - rulScore}). */
    private double healthScore;

    /** Realised incident + realised maintenance + planned maintenance cost (all-time). */
    private double totalCost;

    private IncidentSummary incidentSummary;
    private MaintenanceSummary maintenanceSummary;

    private int rulScore;
    private int remainingDays;
    private String rulAction;

    private String riskLevel;

    /** Forward-looking committed cost = upcoming planned maintenance. */
    private double forecastCost;

    public record IncidentSummary(long count, LocalDate lastDate, double avgSeverity) {
    }

    public record MaintenanceSummary(long preventive, long corrective, long inspection, LocalDate nextPlanned) {
    }
}
