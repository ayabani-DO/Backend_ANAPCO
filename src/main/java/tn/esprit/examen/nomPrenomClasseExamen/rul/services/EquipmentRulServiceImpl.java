package tn.esprit.examen.nomPrenomClasseExamen.rul.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.entities.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.EquipmentRulDto;
import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.RulCategory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentRulServiceImpl implements EquipmentRulService {

    private static final int ANALYSIS_DAYS = 90;

    private final EquipementRepository equipementRepository;
    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;

    @Override
    public EquipmentRulDto computeRul(Long equipmentId) {
        Equipement equipment = equipementRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment not found with id: " + equipmentId));
        return buildRulDto(equipment);
    }

    @Override
    public List<EquipmentRulDto> computeRulForSite(Long siteId) {
        List<Equipement> equipments = equipementRepository.findBySiteIdSite(siteId);
        return equipments.stream()
                .map(this::buildRulDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EquipmentRulDto> getHighRiskEquipment() {
        return equipementRepository.findAll().stream()
                .map(this::buildRulDto)
                .filter(dto -> dto.getRulCategory() == RulCategory.RUL_SHORT)
                .collect(Collectors.toList());
    }

    private EquipmentRulDto buildRulDto(Equipement equipment) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(ANALYSIS_DAYS);

        Date startDate = toDate(windowStart);
        Date endDate = new Date();

        List<Incident> recentIncidents = incidentRepository
                .findByEquipementIdEquipementAndDateBetween(
                        equipment.getIdEquipement(), windowStart, today);

        List<Maintenance> recentMaintenances = maintenanceRepository
                .findByEquipementIdEquipementAndTypeMaintenanceAndDateBetween(
                        equipment.getIdEquipement(), TypeMaintenance.CORRECTIVE, startDate, endDate);

        List<Maintenance> allMaintenances = maintenanceRepository
                .findByEquipementIdEquipement(equipment.getIdEquipement());

        double mtbf = computeMtbf(recentIncidents);
        double mttr = computeMttr(recentIncidents);
        double avgCost = computeAvgCost(recentIncidents, recentMaintenances);

        List<String> factors = new ArrayList<>();
        int score = 0;

        // Factor 1: Incident count (0-25 pts)
        int incidentCount = recentIncidents.size();
        if (incidentCount >= 10) {
            score += 25;
            factors.add("Very high incident frequency: " + incidentCount + " incidents in 90 days");
        } else if (incidentCount >= 6) {
            score += 20;
            factors.add("High incident frequency: " + incidentCount + " incidents in 90 days");
        } else if (incidentCount >= 3) {
            score += 15;
            factors.add("Moderate incident frequency: " + incidentCount + " incidents in 90 days");
        } else if (incidentCount >= 1) {
            score += 8;
            factors.add("Low incident activity: " + incidentCount + " incident(s) in 90 days");
        }

        // Factor 2: Severity distribution (0-25 pts)
        if (incidentCount > 0) {
            double avgWeight = recentIncidents.stream()
                    .filter(i -> i.getSeverityCode() != null)
                    .mapToInt(i -> i.getSeverityCode().getWeight())
                    .average()
                    .orElse(1.0);
            int severityScore = (int) ((avgWeight - 1.0) / 3.0 * 25);
            score += severityScore;
            long criticalCount = recentIncidents.stream()
                    .filter(i -> i.getSeverityCode() == SeverityCode.CRITICAL).count();
            long highCount = recentIncidents.stream()
                    .filter(i -> i.getSeverityCode() == SeverityCode.HIGH).count();
            if (criticalCount > 0) {
                factors.add("Critical incidents detected: " + criticalCount + " critical in last 90 days");
            }
            if (highCount > 0) {
                factors.add("High severity incidents: " + highCount + " high in last 90 days");
            }
        }

        // Factor 3: Corrective maintenance count (0-20 pts)
        int maintenanceCount = recentMaintenances.size();
        if (maintenanceCount >= 6) {
            score += 20;
            factors.add("Excessive corrective maintenance: " + maintenanceCount + " in 90 days");
        } else if (maintenanceCount >= 3) {
            score += 14;
            factors.add("Frequent corrective maintenance: " + maintenanceCount + " in 90 days");
        } else if (maintenanceCount >= 1) {
            score += 8;
            factors.add("Some corrective maintenance: " + maintenanceCount + " in 90 days");
        }

        // Factor 4: Average cost trend (0-15 pts)
        if (avgCost > 20000) {
            score += 15;
            factors.add("Very high maintenance/incident cost: avg " + String.format("%.0f", avgCost));
        } else if (avgCost > 10000) {
            score += 10;
            factors.add("High maintenance/incident cost: avg " + String.format("%.0f", avgCost));
        } else if (avgCost > 3000) {
            score += 5;
            factors.add("Moderate maintenance/incident cost: avg " + String.format("%.0f", avgCost));
        }

        // Factor 5: MTBF (0-10 pts)
        if (mtbf < 10) {
            score += 10;
            factors.add("Very short MTBF: " + String.format("%.1f", mtbf) + " days between failures");
        } else if (mtbf < 20) {
            score += 7;
            factors.add("Short MTBF: " + String.format("%.1f", mtbf) + " days between failures");
        } else if (mtbf < 30) {
            score += 3;
            factors.add("Below-average MTBF: " + String.format("%.1f", mtbf) + " days");
        }

        // Factor 6: MTTR (0-5 pts)
        if (mttr > 14) {
            score += 5;
            factors.add("Very slow resolution time (MTTR): " + String.format("%.1f", mttr) + " days");
        } else if (mttr > 7) {
            score += 3;
            factors.add("Slow resolution time (MTTR): " + String.format("%.1f", mttr) + " days");
        } else if (mttr > 3) {
            score += 1;
        }

        score = Math.min(score, 100);
        RulCategory category = toCategory(score);
        int estimatedDays = estimateDays(score);

        if (factors.isEmpty()) {
            factors.add("No significant degradation indicators in the last 90 days");
        }

        return EquipmentRulDto.builder()
                .equipmentId(equipment.getIdEquipement())
                .equipmentName(equipment.getNomEquipement())
                .equipmentRef(equipment.getRefEquipement())
                .rulScore(score)
                .rulCategory(category)
                .estimatedRemainingDays(estimatedDays)
                .mtbf(mtbf)
                .mttr(mttr)
                .recentIncidentCount(incidentCount)
                .recentCorrectiveMaintenanceCount(maintenanceCount)
                .recentAverageCost(avgCost)
                .mainFactors(factors)
                .recommendedAction(toRecommendedAction(category, score))
                .analysisDate(today)
                .build();
    }

    private double computeMtbf(List<Incident> incidents) {
        if (incidents.isEmpty()) {
            return ANALYSIS_DAYS;
        }
        return (double) ANALYSIS_DAYS / incidents.size();
    }

    private double computeMttr(List<Incident> incidents) {
        List<Incident> closed = incidents.stream()
                .filter(i -> i.getEtatIncident() == EtatIncident.CLOSED
                        && i.getDate() != null
                        && i.getClosedDate() != null)
                .collect(Collectors.toList());

        if (closed.isEmpty()) {
            return 0.0;
        }

        double totalDays = closed.stream()
                .mapToDouble(i -> {
                    long millis = i.getClosedDate().getTime() - i.getDate().getTime();
                    return millis / (1000.0 * 60 * 60 * 24);
                })
                .sum();

        return totalDays / closed.size();
    }

    private double computeAvgCost(List<Incident> incidents, List<Maintenance> maintenances) {
        List<Double> costs = new ArrayList<>();
        incidents.stream()
                .filter(i -> i.getCostReal() != null && i.getCostReal() > 0)
                .forEach(i -> costs.add(i.getCostReal()));
        maintenances.stream()
                .filter(m -> m.getCostReal() != null && m.getCostReal() > 0)
                .forEach(m -> costs.add(m.getCostReal()));

        if (costs.isEmpty()) return 0.0;
        return costs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private RulCategory toCategory(int score) {
        if (score >= 60) return RulCategory.RUL_SHORT;
        if (score >= 30) return RulCategory.RUL_MEDIUM;
        return RulCategory.RUL_LONG;
    }

    private int estimateDays(int score) {
        if (score >= 60) {
            return Math.max(1, 90 - (score - 60) * 2);
        } else if (score >= 30) {
            return 180 - (score - 30) * 3;
        } else {
            return 365 - score * 6;
        }
    }

    private String toRecommendedAction(RulCategory category, int score) {
        return switch (category) {
            case RUL_LONG -> "Continue standard preventive maintenance schedule. No immediate action required.";
            case RUL_MEDIUM -> score >= 50
                    ? "Schedule detailed inspection within 30 days and reinforce preventive maintenance frequency."
                    : "Monitor closely and plan a preventive maintenance visit within 60 days.";
            case RUL_SHORT -> score >= 80
                    ? "Immediate intervention required. Evaluate replacement or major overhaul to avoid failure."
                    : "Plan urgent corrective maintenance. Reduce operational load and prepare contingency parts.";
        };
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
