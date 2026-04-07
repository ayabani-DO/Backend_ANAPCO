package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardKpiDto {
    private IncidentSeverityKpiDto severityKpi;
    private IncidentLifecycleKpiDto lifecycleKpi;
    private IncidentCostKpiDto costKpi;
    private IncidentRecurrenceKpiDto recurrenceKpi;
    private IncidentRiskScoreDto siteRiskScore;
    
    // Additional dashboard summary fields
    private String overallHealthStatus; // HEALTHY, WARNING, CRITICAL
    private String primaryRiskFactor; // Main issue to address
    private String topRecommendation; // Most important action to take
    private java.time.LocalDate lastUpdated;
}
