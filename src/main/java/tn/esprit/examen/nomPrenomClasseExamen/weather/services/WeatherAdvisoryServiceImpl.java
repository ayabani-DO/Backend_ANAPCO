package tn.esprit.examen.nomPrenomClasseExamen.weather.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.RecommendationDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherAlertDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.*;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.RecommendationRepository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.WeatherAlertRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
public class WeatherAdvisoryServiceImpl implements WeatherAdvisoryService {

    private final WeatherAlertRepository weatherAlertRepository;
    private final RecommendationRepository recommendationRepository;

    @Override
    public void generateForAssessment(RiskAssessment assessment) {
        AlertSeverity severity = toSeverity(assessment.getRiskLevel());

        WeatherAlert alert = WeatherAlert.builder()
                .site(assessment.getSite())
                .riskAssessmentId(assessment.getId())
                .title(buildAlertTitle(assessment))
                .message(buildAlertMessage(assessment))
                .severity(severity)
                .status(AlertStatus.OPEN)
                .validUntil(LocalDateTime.now().plusDays(validityDays(assessment.getRiskLevel())))
                .build();
        weatherAlertRepository.save(alert);

        for (Recommendation recommendation : buildRecommendations(assessment)) {
            recommendationRepository.save(recommendation);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeatherAlertDto> getAlertsBySite(Long siteId, AlertStatus status) {
        List<WeatherAlert> alerts = status == null
                ? weatherAlertRepository.findBySiteIdSiteOrderByCreatedAtDesc(siteId)
                : weatherAlertRepository.findBySiteIdSiteAndStatusOrderByCreatedAtDesc(siteId, status);

        return alerts.stream().map(this::toDto).toList();
    }

    @Override
    public WeatherAlertDto updateAlertStatus(Long alertId, AlertStatus status) {
        WeatherAlert alert = weatherAlertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Alert not found with id: " + alertId));
        alert.setStatus(status);
        return toDto(weatherAlertRepository.save(alert));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecommendationDto> getRecommendationsBySite(Long siteId) {
        return recommendationRepository.findBySiteIdSiteOrderByCreatedAtDesc(siteId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private List<Recommendation> buildRecommendations(RiskAssessment assessment) {
        RiskLevel level = assessment.getRiskLevel();

        Recommendation safety = Recommendation.builder()
                .site(assessment.getSite())
                .riskAssessmentId(assessment.getId())
                .category("PERSONNEL")
                .recommendationText(personnelRecommendation(level))
                .priority(priority(level))
                .actionable(true)
                .build();

        Recommendation maintenance = Recommendation.builder()
                .site(assessment.getSite())
                .riskAssessmentId(assessment.getId())
                .category("MAINTENANCE")
                .recommendationText(maintenanceRecommendation(level))
                .priority(priority(level))
                .actionable(true)
                .build();

        Recommendation logistics = Recommendation.builder()
                .site(assessment.getSite())
                .riskAssessmentId(assessment.getId())
                .category("TRANSPORT")
                .recommendationText(transportRecommendation(level))
                .priority(priority(level))
                .actionable(true)
                .build();

        return List.of(safety, maintenance, logistics);
    }

    private String buildAlertTitle(RiskAssessment assessment) {
        return "Weather risk " + assessment.getRiskLevel() + " for site " + assessment.getSite().getNom();
    }

    private String buildAlertMessage(RiskAssessment assessment) {
        return "Risk score " + assessment.getRiskScore() + "/100. Personnel impact: " + assessment.getImpactPersonnel();
    }

    private AlertSeverity toSeverity(RiskLevel level) {
        return switch (level) {
            case LOW -> AlertSeverity.INFO;
            case MEDIUM -> AlertSeverity.WARNING;
            case HIGH, CRITICAL -> AlertSeverity.CRITICAL;
        };
    }

    private int validityDays(RiskLevel level) {
        return switch (level) {
            case CRITICAL -> 1;
            case HIGH -> 2;
            case MEDIUM -> 3;
            case LOW -> 5;
        };
    }

    private String priority(RiskLevel level) {
        return switch (level) {
            case CRITICAL -> "IMMEDIATE";
            case HIGH -> "HIGH";
            case MEDIUM -> "MEDIUM";
            case LOW -> "LOW";
        };
    }

    private String personnelRecommendation(RiskLevel level) {
        return switch (level) {
            case LOW -> "Continue normal operations with standard weather monitoring.";
            case MEDIUM -> "Reinforce PPE usage and schedule additional safety briefings for exposed teams.";
            case HIGH -> "Restrict non-essential outdoor work and deploy safety supervisors for high-risk zones.";
            case CRITICAL -> "Suspend exposed field operations and activate emergency response protocol immediately.";
        };
    }

    private String maintenanceRecommendation(RiskLevel level) {
        return switch (level) {
            case LOW -> "Keep preventive maintenance plan unchanged.";
            case MEDIUM -> "Prioritize inspection of weather-sensitive equipment and critical assets.";
            case HIGH -> "Postpone non-critical jobs and secure vulnerable equipment before weather peak.";
            case CRITICAL -> "Execute emergency maintenance checklist and isolate at-risk systems.";
        };
    }

    private String transportRecommendation(RiskLevel level) {
        return switch (level) {
            case LOW -> "Maintain standard transport routes.";
            case MEDIUM -> "Monitor route conditions and pre-position backup logistics resources.";
            case HIGH -> "Activate alternate routes and reduce heavy-load transfers during risk windows.";
            case CRITICAL -> "Limit transport movements to critical operations only and enable contingency logistics.";
        };
    }

    private WeatherAlertDto toDto(WeatherAlert alert) {
        return WeatherAlertDto.builder()
                .id(alert.getId())
                .siteId(alert.getSite().getIdSite())
                .riskAssessmentId(alert.getRiskAssessmentId())
                .title(alert.getTitle())
                .message(alert.getMessage())
                .severity(alert.getSeverity())
                .status(alert.getStatus())
                .createdAt(alert.getCreatedAt())
                .validUntil(alert.getValidUntil())
                .build();
    }

    private RecommendationDto toDto(Recommendation recommendation) {
        return RecommendationDto.builder()
                .id(recommendation.getId())
                .siteId(recommendation.getSite().getIdSite())
                .riskAssessmentId(recommendation.getRiskAssessmentId())
                .category(recommendation.getCategory())
                .recommendationText(recommendation.getRecommendationText())
                .priority(recommendation.getPriority())
                .actionable(recommendation.getActionable())
                .createdAt(recommendation.getCreatedAt())
                .build();
    }
}
