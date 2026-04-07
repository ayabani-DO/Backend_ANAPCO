package tn.esprit.examen.nomPrenomClasseExamen.weather.dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.AlertSeverity;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.AlertStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class WeatherAlertDto {
    private Long id;
    private Long siteId;
    private Long riskAssessmentId;
    private String title;
    private String message;
    private AlertSeverity severity;
    private AlertStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime validUntil;
}
