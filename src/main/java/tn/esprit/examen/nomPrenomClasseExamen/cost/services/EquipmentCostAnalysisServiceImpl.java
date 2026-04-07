package tn.esprit.examen.nomPrenomClasseExamen.cost.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.CostCategory;
import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.EquipmentCostAnalysisDto;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentCostAnalysisServiceImpl implements EquipmentCostAnalysisService {

    private static final double HIGH_COST_THRESHOLD   = 50_000.0;
    private static final double MEDIUM_COST_THRESHOLD = 10_000.0;

    private final EquipementRepository equipementRepository;
    private final IncidentRepository   incidentRepository;
    private final MaintenanceRepository maintenanceRepository;

    @Override
    public EquipmentCostAnalysisDto computeCostAnalysis(Long equipmentId) {
        Equipement equipment = equipementRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment not found with id: " + equipmentId));
        return buildDto(equipment);
    }

    @Override
    public List<EquipmentCostAnalysisDto> computeCostAnalysisForSite(Long siteId) {
        return equipementRepository.findBySiteIdSite(siteId)
                .stream()
                .map(this::buildDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EquipmentCostAnalysisDto> getTopExpensiveEquipment(int limit) {
        return equipementRepository.findAll()
                .stream()
                .map(this::buildDto)
                .sorted(Comparator.comparingDouble(EquipmentCostAnalysisDto::getTotalCost).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public Map<CostCategory, List<EquipmentCostAnalysisDto>> getEquipmentByCostCategory() {
        List<EquipmentCostAnalysisDto> all = equipementRepository.findAll()
                .stream()
                .map(this::buildDto)
                .collect(Collectors.toList());

        Map<CostCategory, List<EquipmentCostAnalysisDto>> result = new LinkedHashMap<>();
        for (CostCategory cat : CostCategory.values()) {
            result.put(cat, new ArrayList<>());
        }
        all.forEach(dto -> result.get(dto.getCostCategory()).add(dto));
        return result;
    }

    @Override
    public Map<String, Double> getGlobalMonthlyCostTrend() {
        List<Incident>    allIncidents    = incidentRepository.findAll();
        List<Maintenance> allMaintenances = maintenanceRepository.findAll();
        Map<String, Double> trend = new TreeMap<>();
        addIncidentCostsToTrend(allIncidents, trend);
        addMaintenanceCostsToTrend(allMaintenances, trend);
        return trend;
    }

    // -------------------------------------------------------------------------
    // Core builder
    // -------------------------------------------------------------------------

    private EquipmentCostAnalysisDto buildDto(Equipement equipment) {
        Long id = equipment.getIdEquipement();

        List<Incident>    incidents    = incidentRepository.findByEquipementIdEquipement(id);
        List<Maintenance> maintenances = maintenanceRepository.findByEquipementIdEquipement(id);

        // ── Total costs ──────────────────────────────────────────────────────
        double totalIncidentCost = sumCost(incidents.stream()
                .map(Incident::getCostReal).collect(Collectors.toList()));

        // ── Only DONE maintenances have a realized cost ─────────────────────
        double preventiveCost = maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.PREVENTIVE
                        && m.getStatusMaintenance() == StatusMaintenace.DONE
                        && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal).sum();

        double correctiveCost = maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.CORRECTIVE
                        && m.getStatusMaintenance() == StatusMaintenace.DONE
                        && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal).sum();

        double inspectionCost = maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.INSPECTION
                        && m.getStatusMaintenance() == StatusMaintenace.DONE
                        && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal).sum();

        // ── Planned cost = not yet realized (PLANNED status) ─────────────────
        double plannedCost = maintenances.stream()
                .filter(m -> m.getStatusMaintenance() == StatusMaintenace.PLANNED
                        && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal).sum();

        double totalMaintenanceCost = preventiveCost + correctiveCost + inspectionCost;
        double totalCost            = totalIncidentCost + totalMaintenanceCost;
        double forecastTotalCost    = totalCost + plannedCost;

        // ── Cost by severity ─────────────────────────────────────────────────
        Map<String, Double> costBySeverity = incidents.stream()
                .filter(i -> i.getCostReal() != null && i.getSeverityCode() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getSeverityCode().name(),
                        Collectors.summingDouble(Incident::getCostReal)));

        // ── Averages (DONE only for maintenance) ─────────────────────────────
        long incWithCost   = incidents.stream().filter(i -> i.getCostReal() != null).count();
        long maintWithCost = maintenances.stream()
                .filter(m -> m.getStatusMaintenance() == StatusMaintenace.DONE
                        && m.getCostReal() != null)
                .count();

        double avgCostPerIncident    = incWithCost   > 0 ? totalIncidentCost    / incWithCost   : 0.0;
        double avgCostPerMaintenance = maintWithCost > 0 ? totalMaintenanceCost / maintWithCost : 0.0;

        // ── Preventive / corrective ratio ─────────────────────────────────────
        double ratio = correctiveCost > 0 ? preventiveCost / correctiveCost : 0.0;

        // ── Count by type (all statuses) ──────────────────────────────────────
        int preventiveCount  = (int) maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.PREVENTIVE).count();
        int correctiveCount  = (int) maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.CORRECTIVE).count();
        int inspectionCount  = (int) maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.INSPECTION).count();
        int totalTypeCount   = preventiveCount + correctiveCount + inspectionCount;

        double preventivePct  = totalTypeCount > 0 ? (preventiveCount  * 100.0) / totalTypeCount : 0.0;
        double correctivePct  = totalTypeCount > 0 ? (correctiveCount  * 100.0) / totalTypeCount : 0.0;
        double inspectionPct  = totalTypeCount > 0 ? (inspectionCount  * 100.0) / totalTypeCount : 0.0;

        // ── Average cost per type (DONE only) ────────────────────────────────
        long donePrevCount  = maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.PREVENTIVE
                        && m.getStatusMaintenance() == StatusMaintenace.DONE
                        && m.getCostReal() != null).count();
        long doneCorCount   = maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.CORRECTIVE
                        && m.getStatusMaintenance() == StatusMaintenace.DONE
                        && m.getCostReal() != null).count();
        long doneInsCount   = maintenances.stream()
                .filter(m -> m.getTypeMaintenance() == TypeMaintenance.INSPECTION
                        && m.getStatusMaintenance() == StatusMaintenace.DONE
                        && m.getCostReal() != null).count();

        double avgCostPerPreventive  = donePrevCount > 0 ? preventiveCost  / donePrevCount : 0.0;
        double avgCostPerCorrective  = doneCorCount  > 0 ? correctiveCost  / doneCorCount  : 0.0;
        double avgCostPerInspection  = doneInsCount  > 0 ? inspectionCost  / doneInsCount  : 0.0;

        // ── Correlation: incidents ↔ corrective maintenance ───────────────────
        // Ratio close to 1 = each incident generates one corrective maintenance
        double incidentCorrectiveCorrelation = incidents.size() > 0
                ? (double) correctiveCount / incidents.size() : 0.0;

        // ── Monthly trend (DONE maintenances only = realized) ────────────────
        List<Maintenance> doneMaintenances = maintenances.stream()
                .filter(m -> m.getStatusMaintenance() == StatusMaintenace.DONE)
                .collect(Collectors.toList());
        Map<String, Double> monthly = new TreeMap<>();
        addIncidentCostsToTrend(incidents, monthly);
        addMaintenanceCostsToTrend(doneMaintenances, monthly);

        return EquipmentCostAnalysisDto.builder()
                .equipmentId(id)
                .equipmentName(equipment.getNomEquipement())
                .equipmentRef(equipment.getRefEquipement())
                .totalIncidentCost(totalIncidentCost)
                .totalMaintenanceCost(totalMaintenanceCost)
                .totalCost(totalCost)
                .preventiveMaintenanceCost(preventiveCost)
                .correctiveMaintenanceCost(correctiveCost)
                .inspectionMaintenanceCost(inspectionCost)
                .plannedMaintenanceCost(plannedCost)
                .forecastTotalCost(forecastTotalCost)
                .costBySeverity(costBySeverity)
                .averageCostPerIncident(avgCostPerIncident)
                .averageCostPerMaintenance(avgCostPerMaintenance)
                .preventiveCorrectiveCostRatio(ratio)
                .monthlyCostTrend(monthly)
                .costCategory(toCostCategory(totalCost))
                .preventiveCount(preventiveCount)
                .correctiveCount(correctiveCount)
                .inspectionCount(inspectionCount)
                .preventivePercentage(preventivePct)
                .correctivePercentage(correctivePct)
                .inspectionPercentage(inspectionPct)
                .avgCostPerPreventive(avgCostPerPreventive)
                .avgCostPerCorrective(avgCostPerCorrective)
                .avgCostPerInspection(avgCostPerInspection)
                .incidentCorrectiveCorrelation(incidentCorrectiveCorrelation)
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private double sumCost(List<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }

    private void addIncidentCostsToTrend(List<Incident> incidents, Map<String, Double> trend) {
        incidents.stream()
                .filter(i -> i.getCostReal() != null && i.getDate() != null)
                .forEach(i -> trend.merge(toYearMonth(i.getDate()), i.getCostReal(), Double::sum));
    }

    private void addMaintenanceCostsToTrend(List<Maintenance> maintenances, Map<String, Double> trend) {
        maintenances.stream()
                .filter(m -> m.getCostReal() != null && m.getDate() != null)
                .forEach(m -> trend.merge(toYearMonth(m.getDate()), m.getCostReal(), Double::sum));
    }

    private String toYearMonth(Date date) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private CostCategory toCostCategory(double totalCost) {
        if (totalCost >= HIGH_COST_THRESHOLD)   return CostCategory.HIGH_COST;
        if (totalCost >= MEDIUM_COST_THRESHOLD) return CostCategory.MEDIUM_COST;
        return CostCategory.LOW_COST;
    }
}
