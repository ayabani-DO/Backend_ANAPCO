package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentTrendDto {
    private Long siteId;
    private Integer analysisMonths;
    private String trendDirection; // INCREASING, DECREASING, STABLE
    private Double trendPercentage; // Percentage change
    private Long totalIncidentsInPeriod;
    private Double avgIncidentsPerMonth;
    private String trendInterpretation; // Human-readable explanation
}
