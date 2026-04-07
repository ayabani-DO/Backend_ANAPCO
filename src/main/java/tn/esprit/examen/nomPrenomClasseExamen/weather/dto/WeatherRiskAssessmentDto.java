package tn.esprit.examen.nomPrenomClasseExamen.weather.dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.RiskLevel;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.SensitivityLevel;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.SiteType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WeatherRiskAssessmentDto {
    private Long assessmentId;
    private Long siteId;
    private String siteName;
    private Long weatherDataId;
    private LocalDate weatherDate;
    private Integer riskScore;
    private RiskLevel riskLevel;
    private SiteType siteType;
    private SensitivityLevel sensitivityLevel;
    private List<String> riskFactors;
    private String impactPersonnel;
    private String impactEquipment;
    private String impactMaintenance;
    private String impactTransport;
    private String engineType;
    private String engineVersion;
    private LocalDateTime assessedAt;
}
