package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentRiskScoreDto {
    private Long siteId;
    private Long equipmentId; // null if calculating for site
    private String targetName; // Site name or equipment name
    private String targetType; // "SITE" or "EQUIPMENT"
    
    // Risk scoring
    private Integer riskScore; // 0-100
    private String riskLevel; // LOW, MEDIUM, HIGH
    private String riskCategory; // OPERATIONAL, FINANCIAL, SAFETY
    
    // Risk factors (explainable AI)
    private List<String> riskFactors; // Why this score was given
    private List<String> positiveFactors; // Mitigating factors
    
    // Detailed analysis
    private Integer incidentsLast30Days;
    private Integer criticalIncidentsLast90Days;
    private Integer highIncidentsLast30Days;
    private Double avgCostOverrunPercent;
    private Integer repeatedCorrectiveMaintenance;
    private Boolean equipmentOutOfService;
    
    // Recommendations
    private String recommendation; // Actionable advice
    private String urgencyLevel; // IMMEDIATE, WITHIN_7_DAYS, WITHIN_30_DAYS
    private List<String> suggestedActions; // Specific steps to take
    
    // Metadata
    private java.time.LocalDate analysisDate;
    private Integer dataPointsAnalyzed; // Number of incidents considered
    private Double confidenceScore; // How confident we are in this assessment (0-1)
}
