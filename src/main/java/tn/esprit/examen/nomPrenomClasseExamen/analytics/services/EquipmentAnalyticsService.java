package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.EquipmentAnalyticsDTO;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.EquipmentRulDto;
import tn.esprit.examen.nomPrenomClasseExamen.rul.services.EquipmentRulService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Analytics Layer — consolidated per-equipment health view.
 *
 * <p>It <b>consumes</b> {@link OperationalAnalyticsService} (canonical operational primitives) and the
 * existing {@link EquipmentRulService}; it does not re-implement any incident/maintenance/RUL formula.
 * This is the equipment-level counterpart the legacy {@code EquipmentCostAnalysisService} will delegate
 * to in a later step.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentAnalyticsService {

    private final EquipementRepository equipementRepository;
    private final OperationalAnalyticsService operationalAnalytics;
    private final EquipmentRulService rulService;

    public EquipmentAnalyticsDTO getEquipmentAnalytics(Long equipmentId) {
        Equipement equipment = equipementRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment not found with id: " + equipmentId));
        return buildDto(equipment);
    }

    /**
     * Ranking of equipment. {@code by} = "risk" (RUL score desc) or "cost" (total cost desc, default).
     */
    public List<EquipmentAnalyticsDTO> getRanking(String by, int limit) {
        boolean byRisk = "risk".equalsIgnoreCase(by);
        Comparator<EquipmentAnalyticsDTO> comparator = byRisk
                ? Comparator.comparingInt(EquipmentAnalyticsDTO::getRulScore).reversed()
                : Comparator.comparingDouble(EquipmentAnalyticsDTO::getTotalCost).reversed();

        return equipementRepository.findAll().stream()
                .map(this::buildDto)
                .sorted(comparator)
                .limit(Math.max(1, limit))
                .toList();
    }

    // ── Core builder (consumes Analytics primitives + RUL) ──

    private EquipmentAnalyticsDTO buildDto(Equipement equipment) {
        Long id = equipment.getIdEquipement();

        List<Incident> incidents = operationalAnalytics.equipmentIncidents(id);
        List<Maintenance> maintenances = operationalAnalytics.equipmentMaintenances(id);

        double incidentCost = operationalAnalytics.totalIncidentCost(incidents);
        double realisedMaintenanceCost = operationalAnalytics.realisedMaintenanceCost(maintenances);
        double plannedMaintenanceCost = operationalAnalytics.plannedMaintenanceCost(maintenances);
        // Canonical: realised only. Planned maintenance is future/committed spend, reported separately.
        double operationalCost = operationalAnalytics.operationalCost(incidents, maintenances);

        // RUL engine already encapsulates incidents + maintenance degradation signals.
        EquipmentRulDto rul = rulService.computeRul(id);
        double healthScore = Math.max(0, Math.min(100, 100 - rul.getRulScore()));

        EquipmentAnalyticsDTO.IncidentSummary incidentSummary = new EquipmentAnalyticsDTO.IncidentSummary(
                incidents.size(),
                lastIncidentDate(incidents),
                round2(operationalAnalytics.severityIndex(incidents)));

        EquipmentAnalyticsDTO.MaintenanceSummary maintenanceSummary = new EquipmentAnalyticsDTO.MaintenanceSummary(
                operationalAnalytics.countByType(maintenances, TypeMaintenance.PREVENTIVE),
                operationalAnalytics.countByType(maintenances, TypeMaintenance.CORRECTIVE),
                operationalAnalytics.countByType(maintenances, TypeMaintenance.INSPECTION),
                nextPlannedDate(maintenances));

        return EquipmentAnalyticsDTO.builder()
                .equipmentId(id)
                .equipmentName(equipment.getNomEquipement())
                .siteName(equipment.getSite() != null ? equipment.getSite().getNom() : null)
                .currency(equipment.getSite() != null ? equipment.getSite().getCurrencyCode() : null)
                .healthScore(round2(healthScore))
                .incidentRealCost(round2(incidentCost))
                .realisedMaintenanceCost(round2(realisedMaintenanceCost))
                .operationalCost(round2(operationalCost))
                .plannedMaintenanceCost(round2(plannedMaintenanceCost))
                .totalCost(round2(operationalCost))
                .incidentSummary(incidentSummary)
                .maintenanceSummary(maintenanceSummary)
                .rulScore(rul.getRulScore())
                .remainingDays(rul.getEstimatedRemainingDays())
                .rulAction(rul.getRecommendedAction())
                .riskLevel(riskLevelFromScore(rul.getRulScore()))
                .forecastCost(round2(plannedMaintenanceCost))
                .build();
    }

    // ── Small local derivations (dates only — no KPI formula duplicated) ──

    private LocalDate lastIncidentDate(List<Incident> incidents) {
        return incidents.stream()
                .map(Incident::getDate)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(this::toLocalDate)
                .orElse(null);
    }

    private LocalDate nextPlannedDate(List<Maintenance> maintenances) {
        return maintenances.stream()
                .filter(m -> m.getStatusMaintenance() == StatusMaintenace.PLANNED && m.getDate() != null)
                .map(Maintenance::getDate)
                .min(Comparator.naturalOrder())
                .map(this::toLocalDate)
                .orElse(null);
    }

    private String riskLevelFromScore(int rulScore) {
        if (rulScore >= 60) return "HIGH";
        if (rulScore >= 30) return "MEDIUM";
        return "LOW";
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
