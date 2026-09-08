package tn.esprit.examen.nomPrenomClasseExamen.cost.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class EquipmentCostAnalysisDto {
    private Long equipmentId;
    private String equipmentName;
    private String equipmentRef;
    private double totalIncidentCost;
    /** Realised (DONE) maintenance cost — same canonical definition as the Analytics/Financial layers. */
    private double totalMaintenanceCost;
    /** totalIncidentCost + totalMaintenanceCost (realised only). */
    private double operationalCost;
    /** @deprecated use {@link #operationalCost} — identical value. */
    @Deprecated
    private double totalCost;
    private double preventiveMaintenanceCost;
    private double correctiveMaintenanceCost;
    private double inspectionMaintenanceCost;
    /** Future/committed maintenance (PLANNED + IN_PROGRESS). Not part of realised cost. */
    private double plannedMaintenanceCost;
    /** @deprecated committed total = operationalCost + plannedMaintenanceCost. Not a forecast. */
    @Deprecated
    private double forecastTotalCost;
    private Map<String, Double> costBySeverity;
    private double averageCostPerIncident;
    private double averageCostPerMaintenance;
    private double preventiveCorrectiveCostRatio;
    private Map<String, Double> monthlyCostTrend;
    private CostCategory costCategory;
    private int preventiveCount;
    private int correctiveCount;
    private int inspectionCount;
    private double preventivePercentage;
    private double correctivePercentage;
    private double inspectionPercentage;
    private double avgCostPerPreventive;
    private double avgCostPerCorrective;
    private double avgCostPerInspection;
    private double incidentCorrectiveCorrelation;
}
