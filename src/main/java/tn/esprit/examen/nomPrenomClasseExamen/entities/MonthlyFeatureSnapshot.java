package tn.esprit.examen.nomPrenomClasseExamen.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_feature_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "uq_snapshot_site_year_month", columnNames = {"site_id", "year", "month"}),
        indexes = {
                @Index(name = "idx_snapshot_site", columnList = "site_id"),
                @Index(name = "idx_snapshot_year_month", columnList = "year, month")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyFeatureSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Sites site;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    // ── Cost features ──────────────────────────────────────
    private Double totalCostEur;
    private Double previousMonthTotalCostEur;
    private Double incidentCostEur;
    private Double maintenanceCostEur;
    private Double manualExpenseEur;
    private Double budgetEur;
    private Double budgetVariancePct;

    // ── Incident features ──────────────────────────────────
    private Integer incidentCount;
    private Integer criticalIncidentCount;
    private Integer highIncidentCount;
    private Double avgIncidentSeverity;

    // ── Maintenance features ───────────────────────────────
    private Integer preventiveMaintenanceCount;
    private Integer correctiveMaintenanceCount;
    private Integer inspectionCount;
    private Double correctivePreventiveRatio;

    // ── Reliability features ───────────────────────────────
    private Double avgMtbf;
    private Double avgMttr;

    // ── External market features ───────────────────────────
    private Double oilPriceAvgUsd;
    private Double gasPriceAvgEurMwh;
    private Double electricityPriceAvgEurMwh;

    // ── Weather features ───────────────────────────────────
    private Double weatherRiskScoreAvg;
    private Integer weatherAlertCount;

    // ── Site/equipment categorical features ────────────────
    private String siteType;
    private Integer equipmentCount;
    private String dominantEquipmentCategory;
    private Integer season; // 1=Winter, 2=Spring, 3=Summer, 4=Autumn

    // ── Target labels (filled retrospectively for supervised learning) ──
    private Double nextMonthTotalCostEur;   // TARGET for Model 1 (cost forecast)
    private String riskClass;               // TARGET for Model 2 (LOW_RISK / MEDIUM_RISK / HIGH_RISK)

    // ── Metadata ───────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime computedAt;

    @PrePersist
    public void prePersist() {
        if (this.computedAt == null) {
            this.computedAt = LocalDateTime.now();
        }
    }
}
