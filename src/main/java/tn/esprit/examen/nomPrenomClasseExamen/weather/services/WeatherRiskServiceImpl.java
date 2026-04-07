package tn.esprit.examen.nomPrenomClasseExamen.weather.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusSites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.dto.WeatherRiskAssessmentDto;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.*;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.RiskAssessmentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.WeatherDataRepository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.risk.RiskComputationResult;
import tn.esprit.examen.nomPrenomClasseExamen.weather.risk.WeatherRiskRuleEngine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional
public class WeatherRiskServiceImpl implements WeatherRiskService {

    private final SitesRepository sitesRepository;
    private final WeatherDataRepository weatherDataRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final WeatherRiskRuleEngine weatherRiskRuleEngine;
    private final WeatherAdvisoryService weatherAdvisoryService;

    @Override
    public WeatherRiskAssessmentDto assessLatestWeatherRisk(Long siteId) {
        Sites site = findSite(siteId);
        WeatherData latestWeatherData = weatherDataRepository
                .findFirstBySiteIdSiteAndSourceTypeOrderByDataDateDesc(siteId, WeatherSourceType.FORECAST)
                .or(() -> weatherDataRepository.findFirstBySiteIdSiteOrderByDataDateDesc(siteId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No weather data found for site id: " + siteId));

        // Idempotency: return existing assessment if this exact weather record was already assessed
        Optional<RiskAssessment> existing = riskAssessmentRepository.findFirstByWeatherDataId(latestWeatherData.getId());
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        SiteType siteType = inferSiteType(site);
        SensitivityLevel sensitivityLevel = inferSensitivity(site);

        RiskComputationResult result = weatherRiskRuleEngine.compute(latestWeatherData, siteType, sensitivityLevel);

        RiskAssessment assessment = RiskAssessment.builder()
                .site(site)
                .weatherData(latestWeatherData)
                .riskScore(result.getRiskScore())
                .riskLevel(result.getRiskLevel())
                .siteType(siteType)
                .sensitivityLevel(sensitivityLevel)
                .riskFactors(String.join(" | ", result.getFactors()))
                .impactPersonnel(result.getImpactPersonnel())
                .impactEquipment(result.getImpactEquipment())
                .impactMaintenance(result.getImpactMaintenance())
                .impactTransport(result.getImpactTransport())
                .engineType("RULE_BASED")
                .engineVersion("1.0")
                .build();

        RiskAssessment savedAssessment = riskAssessmentRepository.save(assessment);
        weatherAdvisoryService.generateForAssessment(savedAssessment);

        return toDto(savedAssessment);
    }

    @Override
    @Transactional(readOnly = true)
    public WeatherRiskAssessmentDto getLatestAssessment(Long siteId) {
        RiskAssessment assessment = riskAssessmentRepository.findFirstBySiteIdSiteOrderByAssessedAtDesc(siteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No risk assessment found for site id: " + siteId));
        return toDto(assessment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WeatherRiskAssessmentDto> getAssessmentHistory(Long siteId) {
        return riskAssessmentRepository.findBySiteIdSiteOrderByAssessedAtDesc(siteId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private Sites findSite(Long siteId) {
        return sitesRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Site not found with id: " + siteId));
    }

    private SiteType inferSiteType(Sites site) {
        if (site.getSiteType() != null) {
            return site.getSiteType();
        }
        String codeRef = site.getCodeRef() == null ? "" : site.getCodeRef().toUpperCase();
        String name = site.getNom() == null ? "" : site.getNom().toUpperCase();
        String fingerprint = codeRef + " " + name;

        if (fingerprint.contains("WELL")) return SiteType.WELL_PAD;
        if (fingerprint.contains("GAS") || fingerprint.contains("SEPARATION")) return SiteType.GAS_SEPARATION_UNIT;
        if (fingerprint.contains("PROCESS")) return SiteType.PROCESSING_FACILITY;
        if (fingerprint.contains("TREATMENT") || fingerprint.contains("HEAVY")) return SiteType.TREATMENT_PLANT;
        if (fingerprint.contains("STORAGE") || fingerprint.contains("TERMINAL")) return SiteType.STORAGE_TERMINAL;

        return SiteType.OTHER;
    }

    private SensitivityLevel inferSensitivity(Sites site) {
        StatusSites status = site.getStatusSites();
        if (status == null) {
            return SensitivityLevel.MEDIUM;
        }
        return switch (status) {
            case ACTIF -> SensitivityLevel.HIGH;
            case SUSPENDUE -> SensitivityLevel.MEDIUM;
            case FERME -> SensitivityLevel.LOW;
        };
    }

    private WeatherRiskAssessmentDto toDto(RiskAssessment assessment) {
        List<String> factors = assessment.getRiskFactors() == null || assessment.getRiskFactors().isBlank()
                ? Collections.emptyList()
                : Arrays.stream(assessment.getRiskFactors().split("\\s*\\|\\s*"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        return WeatherRiskAssessmentDto.builder()
                .assessmentId(assessment.getId())
                .siteId(assessment.getSite().getIdSite())
                .siteName(assessment.getSite().getNom())
                .weatherDataId(assessment.getWeatherData() != null ? assessment.getWeatherData().getId() : null)
                .weatherDate(assessment.getWeatherData() != null ? assessment.getWeatherData().getDataDate() : null)
                .riskScore(assessment.getRiskScore())
                .riskLevel(assessment.getRiskLevel())
                .siteType(assessment.getSiteType())
                .sensitivityLevel(assessment.getSensitivityLevel())
                .riskFactors(factors)
                .impactPersonnel(assessment.getImpactPersonnel())
                .impactEquipment(assessment.getImpactEquipment())
                .impactMaintenance(assessment.getImpactMaintenance())
                .impactTransport(assessment.getImpactTransport())
                .engineType(assessment.getEngineType())
                .engineVersion(assessment.getEngineVersion())
                .assessedAt(assessment.getAssessedAt())
                .build();
    }
}
