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
public class SiteComparisonDto {
    private List<Long> comparedSiteIds;
    private Integer year;
    private Integer month;
    
    // Comparison metrics
    private Map<Long, String> siteNames;
    private Map<Long, Double> severityIndices;
    private Map<Long, Double> closureRates;
    private Map<Long, Double> totalCosts;
    private Map<Long, Integer> riskScores;
    
    // Rankings
    private List<Long> sitesByPerformance; // Best to worst
    private List<Long> sitesByRisk; // Lowest to highest risk
    private List<Long> sitesByCost; // Lowest to highest cost
    
    // Insights
    private String bestPerformingSite;
    private String highestRiskSite;
    private String mostCostlySite;
    private String keyFindings;
}
