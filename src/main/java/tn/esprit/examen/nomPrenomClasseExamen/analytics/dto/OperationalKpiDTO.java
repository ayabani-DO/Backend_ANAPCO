package tn.esprit.examen.nomPrenomClasseExamen.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Consolidated operational health of a site for a given month — the single source of truth
 * for incident + maintenance KPIs, produced by the Analytics Layer
 * ({@code OperationalAnalyticsService}).
 *
 * <p>Since 1B all cost fields are normalised to the reporting currency (see {@link #currency});
 * {@link #fxComplete} / {@link #fxUnavailable} disclose any line that could not be converted.
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

    // ── Canonical cost fields (reporting currency) ───────────
    /** Reporting currency all cost fields are expressed in. */
    private String currency;
    /** Σ Incident.costReal for the month. */
    private double incidentRealCost;
    /** Σ Maintenance.costReal where status == DONE. */
    private double realisedMaintenanceCost;
    /** Σ Maintenance.costReal where status ∈ {PLANNED, IN_PROGRESS} — future/committed, NOT spent. */
    private double plannedMaintenanceCost;
    /** incidentRealCost + realisedMaintenanceCost (planned maintenance is never included). */
    private double operationalCost;
    /** false when at least one line could not be converted to {@link #currency}. */
    private boolean fxComplete;
    /** Lines excluded from the totals above because their FX rate was unavailable. */
    private List<FxGap> fxUnavailable;

    // ── Realised operational cost — legacy aliases ───────────
    /** @deprecated use {@link #incidentRealCost}. Now normalised to the reporting currency. */
    @Deprecated
    private double incidentCost;
    /** @deprecated use {@link #realisedMaintenanceCost}. Now normalised to the reporting currency. */
    @Deprecated
    private double maintenanceCost;
    /** @deprecated use {@link #operationalCost}. Now normalised to the reporting currency. */
    @Deprecated
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
