package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analytics Layer — single source of truth for a site's operational health (incidents + maintenance).
 *
 * <p>It centralises calculations that were previously duplicated (with divergent formulas) across
 * {@code IncidentKpiService}, {@code EquipmentCostAnalysisServiceImpl}, {@code EquipmentRulServiceImpl}
 * and {@code MonthlyFeatureAggregationService}. The Decision, AI and Chatbot layers are meant to
 * consume this service instead of re-reading the repositories.
 *
 * <p>The primitive calculators are kept {@code public} so downstream layers can reuse the exact same
 * numbers. Formulas mirror the existing production code so the consolidated figures stay consistent
 * with the legacy endpoints:
 * <ul>
 *   <li>severity index = weighted mean of {@link SeverityCode#getWeight()} (from IncidentKpiService)</li>
 *   <li>MTTR = mean resolution time of CLOSED incidents in days (IncidentKpiService / RUL engine)</li>
 *   <li>MTBF = window length / incident count at site level (canonical, from the RUL engine intent)</li>
 *   <li>maintenance cost = realised (DONE) maintenance cost (EquipmentCostAnalysis semantics)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationalAnalyticsService {

    private static final int TOP_RECURRING_LIMIT = 5;

    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;

    /**
     * Consolidated operational KPIs for one site and one month.
     */
    public OperationalKpiDTO getOperationalKpi(Long siteId, int year, int month) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        int windowDays = monthEnd.getDayOfMonth();

        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(
                siteId, toDate(monthStart), toDate(monthEnd));
        List<Maintenance> maintenances = maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(
                siteId, toDate(monthStart), toDate(monthEnd));

        double incidentCost = totalIncidentCost(incidents);
        double maintenanceCost = realisedMaintenanceCost(maintenances);
        double mttr = averageMttr(incidents);
        double mtbf = averageMtbf(incidents.size(), windowDays);

        return OperationalKpiDTO.builder()
                .siteId(siteId)
                .year(year)
                .month(month)
                .incidentCount(incidents.size())
                .criticalIncidentCount(countBySeverity(incidents, SeverityCode.CRITICAL))
                .preventiveCount(countByType(maintenances, TypeMaintenance.PREVENTIVE))
                .correctiveCount(countByType(maintenances, TypeMaintenance.CORRECTIVE))
                .inspectionCount(countByType(maintenances, TypeMaintenance.INSPECTION))
                .incidentCost(round2(incidentCost))
                .maintenanceCost(round2(maintenanceCost))
                .totalOperationalCost(round2(incidentCost + maintenanceCost))
                .averageMTTR(round2(mttr))
                .averageMTBF(round2(mtbf))
                .severityIndex(round2(severityIndex(incidents)))
                .recurrenceRate(round2(recurrenceRate(incidents)))
                .availability(round2(availability(mtbf, mttr)))
                .topRecurringEquipments(topRecurringEquipments(incidents))
                .build();
    }

    // ── Public primitive calculators (reused by Decision / AI / Chatbot layers) ──

    public long countBySeverity(List<Incident> incidents, SeverityCode severity) {
        return incidents.stream().filter(i -> i.getSeverityCode() == severity).count();
    }

    public long countByType(List<Maintenance> maintenances, TypeMaintenance type) {
        return maintenances.stream().filter(m -> m.getTypeMaintenance() == type).count();
    }

    /** Weighted mean incident severity (1..4); 0 when there are no incidents. */
    public double severityIndex(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        double totalWeight = incidents.stream()
                .filter(i -> i.getSeverityCode() != null)
                .mapToInt(i -> i.getSeverityCode().getWeight())
                .sum();
        return totalWeight / incidents.size();
    }

    /** Percentage of incidents that occurred on equipment hit more than once in the window. */
    public double recurrenceRate(List<Incident> incidents) {
        if (incidents.isEmpty()) return 0.0;
        long repeated = incidents.stream()
                .filter(i -> i.getEquipement() != null)
                .collect(Collectors.groupingBy(i -> i.getEquipement().getIdEquipement(), Collectors.counting()))
                .values().stream()
                .filter(count -> count > 1)
                .count();
        return (repeated * 100.0) / incidents.size();
    }

    /** Mean time to repair (days) over CLOSED incidents with both a start and a closed date. */
    public double averageMttr(List<Incident> incidents) {
        List<Incident> closed = incidents.stream()
                .filter(i -> i.getEtatIncident() == EtatIncident.CLOSED
                        && i.getDate() != null
                        && i.getClosedDate() != null)
                .toList();
        if (closed.isEmpty()) return 0.0;
        double totalDays = closed.stream()
                .mapToDouble(i -> (i.getClosedDate().getTime() - i.getDate().getTime()) / (1000.0 * 60 * 60 * 24))
                .sum();
        return totalDays / closed.size();
    }

    /** Site-level mean time between failures (days) = window length / incident count. */
    public double averageMtbf(int incidentCount, int windowDays) {
        if (incidentCount <= 0) return windowDays;
        return (double) windowDays / incidentCount;
    }

    /** Availability as a percentage: MTBF / (MTBF + MTTR). 100% when there is no downtime. */
    public double availability(double mtbf, double mttr) {
        double denominator = mtbf + mttr;
        if (denominator <= 0) return 100.0;
        return (mtbf / denominator) * 100.0;
    }

    public double totalIncidentCost(List<Incident> incidents) {
        return incidents.stream()
                .filter(i -> i.getCostReal() != null)
                .mapToDouble(Incident::getCostReal)
                .sum();
    }

    /** Realised maintenance cost = DONE maintenances that carry a real cost. */
    public double realisedMaintenanceCost(List<Maintenance> maintenances) {
        return maintenances.stream()
                .filter(m -> m.getStatusMaintenance() == StatusMaintenace.DONE && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal)
                .sum();
    }

    /** Planned (not-yet-realised) maintenance cost = PLANNED maintenances that carry a cost. */
    public double plannedMaintenanceCost(List<Maintenance> maintenances) {
        return maintenances.stream()
                .filter(m -> m.getStatusMaintenance() == StatusMaintenace.PLANNED && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal)
                .sum();
    }

    /** Incidents recorded for a whole site within a given month (canonical operational data source). */
    public List<Incident> siteIncidents(Long siteId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        return incidentRepository.findBySitesIdSiteAndDateBetween(siteId, toDate(start), toDate(end));
    }

    /** Maintenances recorded for a whole site within a given month. */
    public List<Maintenance> siteMaintenances(Long siteId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        return maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(siteId, toDate(start), toDate(end));
    }

    /** All incidents ever recorded for one piece of equipment (source data for equipment analytics). */
    public List<Incident> equipmentIncidents(Long equipmentId) {
        return incidentRepository.findByEquipementIdEquipement(equipmentId);
    }

    /** All maintenances ever recorded for one piece of equipment. */
    public List<Maintenance> equipmentMaintenances(Long equipmentId) {
        return maintenanceRepository.findByEquipementIdEquipement(equipmentId);
    }

    /** Equipment names hit more than once, ordered by incident count (desc), capped. */
    public List<String> topRecurringEquipments(List<Incident> incidents) {
        return incidents.stream()
                .filter(i -> i.getEquipement() != null && i.getEquipement().getNomEquipement() != null)
                .collect(Collectors.groupingBy(i -> i.getEquipement().getNomEquipement(), Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_RECURRING_LIMIT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
