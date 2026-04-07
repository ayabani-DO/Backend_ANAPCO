package tn.esprit.examen.nomPrenomClasseExamen.weather.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecommendationDto {
    private Long id;
    private Long siteId;
    private Long riskAssessmentId;
    private String category;
    private String recommendationText;
    private String priority;
    private Boolean actionable;
    private LocalDateTime createdAt;
}
