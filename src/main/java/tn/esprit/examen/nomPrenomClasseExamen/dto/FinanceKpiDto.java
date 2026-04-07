package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class FinanceKpiDto {
    private Long siteId;
    private Integer year;
    private Integer month;
    private Double budgetEur;
    private Double realEur;
    private Double varianceEur;
    private Double variancePercent;
    private Double forecastEur;
    private Double varianceForecastEur;
    private Double varianceForecastPercent;
    private String riskLevel;
}
