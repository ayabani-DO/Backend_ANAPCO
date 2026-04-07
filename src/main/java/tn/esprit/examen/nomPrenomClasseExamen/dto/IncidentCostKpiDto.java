package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentCostKpiDto {
    private Long siteId;
    private Integer year;
    private Integer month;
    private String targetCurrency; // Usually EUR for standardization
    
    // Total costs
    private Double totalEstimatedCost;
    private Double totalRealCost;
    private Double totalCostVariance; // Real - Estimated
    private Double totalCostVariancePercent; // (Real - Estimated) / Estimated * 100
    
    // Averages
    private Double avgCostPerIncident;
    private Double avgEstimatedCostPerIncident;
    private Double avgRealCostPerIncident;
    
    // Cost breakdown by severity
    private Map<String, Double> costBySeverity;
    
    // Cost breakdown by equipment category
    private Map<String, Double> costByEquipmentCategory;
    
    // Financial insights
    private String costPerformanceLevel; // UNDER_BUDGET, ON_BUDGET, OVER_BUDGET
    private Double costOverrunRatio; // Over budget incidents / total incidents * 100
    private Long totalIncidentsWithCosts;
    
    // Currency conversion info
    private String originalCurrency;
    private Double exchangeRate;
}
