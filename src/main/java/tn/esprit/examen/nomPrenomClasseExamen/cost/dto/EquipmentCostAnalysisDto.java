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
    private double totalMaintenanceCost;
    private double totalCost;
    private double preventiveMaintenanceCost;
    private double correctiveMaintenanceCost;
    private double inspectionMaintenanceCost;
    private double plannedMaintenanceCost;
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
