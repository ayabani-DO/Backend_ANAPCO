package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentLifecycleKpiDto {
    private Long siteId;
    private Integer year;
    private Integer month;
    
    // Status counts
    private Long openCount;
    private Long inProgressCount;
    private Long closedCount;
    private Long totalCount;
    
    // Performance metrics
    private Double closureRate; // Closed incidents / total incidents * 100
    private Double avgResolutionDays; // Average time from creation to closure
    private Double avgResolutionHours; // More precise timing
    private Long longestResolutionDays;
    private Long shortestResolutionDays;
    
    // Operational insights
    private String performanceLevel; // EXCELLENT, GOOD, POOR based on closure rate
    private Double openIncidentRatio; // Open incidents / total incidents * 100
    private Double inProgressRatio; // In-progress incidents / total incidents * 100
}
