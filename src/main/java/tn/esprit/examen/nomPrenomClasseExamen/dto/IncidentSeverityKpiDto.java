package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentSeverityKpiDto {
    private Long siteId;
    private Integer year;
    private Integer month;
    
    // Counts by severity
    private Long lowCount;
    private Long mediumCount;
    private Long highCount;
    private Long criticalCount;
    private Long totalCount;
    
    // Percentages
    private Double lowPercentage;
    private Double mediumPercentage;
    private Double highPercentage;
    private Double criticalPercentage;
    
    // Advanced metrics
    private Double severityIndex; // Weighted average: (1*LOW + 2*MEDIUM + 3*HIGH + 4*CRITICAL) / total
    private Double criticalRatio; // Critical incidents / total incidents * 100
    
    // Risk classification
    private String riskLevel; // LOW, MEDIUM, HIGH based on severityIndex
}
