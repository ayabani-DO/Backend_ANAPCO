package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentRecurrenceKpiDto {
    private Long siteId;
    private Integer analysisDays; // Period analyzed (e.g., 30 days)
    
    // Recurrence metrics
    private Long totalIncidents;
    private Long repeatedIncidents; // Incidents on same equipment/site
    private Double recurrenceRate; // Repeated incidents / total incidents * 100
    
    // Equipment recurrence
    private Map<String, Long> incidentsByEquipment; // Equipment -> incident count
    private List<String> highRiskEquipment; // Equipment with >= 3 incidents
    private Long equipmentWithMultipleIncidents;
    
    // Site recurrence patterns
    private Map<String, Long> incidentsBySite; // Site -> incident count
    private List<String> highRiskSites; // Sites with >= 5 HIGH/CRITICAL incidents
    
    // Severity recurrence
    private Map<String, Long> incidentsBySeverity;
    private Long criticalIncidentsLastPeriod;
    private Long highIncidentsLastPeriod;
    
    // Risk indicators
    private String overallRiskLevel; // LOW, MEDIUM, HIGH
    private List<String> riskFactors; // Explanation of risk level
    private List<String> recommendations; // Actionable recommendations
    
    // Trend analysis
    private Double incidentTrend; // Percentage change from previous period
    private String trendDirection; // INCREASING, DECREASING, STABLE
}
