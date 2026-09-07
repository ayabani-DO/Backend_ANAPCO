package tn.esprit.examen.nomPrenomClasseExamen.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Consolidated operational health of a site for a given month — the single source of truth
 * for incident + maintenance KPIs, produced by the Analytics Layer
 * ({@code OperationalAnalyticsService}).
 *
 * <p>Costs here are the raw realised amounts in the underlying records (no FX normalisation);
 * currency-normalised financial figures belong to the Financial Analytics layer.
 */
@Data
@Builder
public class OperationalKpiDTO {

    private Long siteId;
    private Integer year;
    private Integer month;

    // ── Incident volume ──────────────────────────────────────
    private long incidentCount;
    private long criticalIncidentCount;

    // ── Maintenance volume (all statuses) ────────────────────
    private long preventiveCount;
    private long correctiveCount;
    private long inspectionCount;

    // ── Realised operational cost ────────────────────────────
    private double incidentCost;
    private double maintenanceCost;
    private double totalOperationalCost;

    // ── Reliability ──────────────────────────────────────────
    private double averageMTTR;   // mean time to repair, in days
    private double averageMTBF;   // mean time between failures, in days

    // ── Risk indicators ──────────────────────────────────────
    private double severityIndex; // weighted mean incident severity (1..4)
    private double recurrenceRate; // % of incidents on equipment hit more than once
    private double availability;  // MTBF / (MTBF + MTTR) as a percentage

    // ── Hotspots ─────────────────────────────────────────────
    private List<String> topRecurringEquipments;
}
