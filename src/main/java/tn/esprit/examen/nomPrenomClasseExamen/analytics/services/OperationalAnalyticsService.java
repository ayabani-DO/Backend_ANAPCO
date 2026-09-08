package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalCostBreakdown;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EtatIncident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Incident;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.entities.SeverityCode;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.entities.StatusMaintenace;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TypeMaintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.IncidentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analytics Layer — single source of truth for a site's operational health (incidents + maintenance)
 * <b>and the canonical owner of the {@code operationalCost} subtotal</b>.
 *
 * <p>Canonical cost definitions (see {@link #operationalCost(Long, int, int)}):
 * <ul>
 *   <li>{@code incidentRealCost}        = Σ {@code Incident.costReal} in the period</li>
 *   <li>{@code realisedMaintenanceCost} = Σ {@code Maintenance.costReal} where status == DONE</li>
 *   <li>{@code plannedMaintenanceCost}  = Σ {@code Maintenance.costReal} where status ∈ {PLANNED, IN_PROGRESS}</li>
 *   <li>{@code operationalCost}         = incidentRealCost + realisedMaintenanceCost
 *       (planned maintenance is <b>never</b> part of it)</li>
 * </ul>
 * All of the above are normalised to the reporting currency (via {@link CurrencyConverter}); the
 * Financial layer consumes this subtotal and adds manual expenses to build {@code totalRealCost}.
 * This service does <b>not</b> aggregate {@code ManualExpense}.
 *
 * <p>The list-based primitive calculators stay {@code public} and currency-agnostic (raw
 * {@code costReal} sums) so equipment-level callers share the exact same definitions.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationalAnalyticsService {

    private static final int TOP_RECURRING_LIMIT = 5;

    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final SitesRepository sitesRepository;
    private final CurrencyConverter currencyConverter;

    /**
     * Consolidated operational KPIs for one site and one month. Cost fields are normalised to the
     * reporting currency; {@code fxComplete}/{@code fxUnavailable} disclose any line that could not
     * be converted.
     */
    public OperationalKpiDTO getOperationalKpi(Long siteId, int year, int month) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        int windowDays = monthEnd.getDayOfMonth();

        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(
                siteId, toDate(monthStart), toDate(monthEnd));
        List<Maintenance> maintenances = maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(
                siteId, toDate(monthStart), toDate(monthEnd));

        OperationalCostBreakdown cost = operationalCost(siteId, year, month, incidents, maintenances);

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
                // ── Canonical cost fields (reporting currency) ──
                .currency(cost.currency())
                .incidentRealCost(cost.incidentRealCost())
                .realisedMaintenanceCost(cost.realisedMaintenanceCost())
                .plannedMaintenanceCost(cost.plannedMaintenanceCost())
                .operationalCost(cost.operationalCost())
                .fxComplete(cost.fxComplete())
                .fxUnavailable(cost.fxUnavailable())
                // ── Legacy aliases (kept for compatibility; now in reporting currency) ──
                .incidentCost(cost.incidentRealCost())
                .maintenanceCost(cost.realisedMaintenanceCost())
                .totalOperationalCost(cost.operationalCost())
                .averageMTTR(round2(mttr))
                .averageMTBF(round2(mtbf))
                .severityIndex(round2(severityIndex(incidents)))
                .recurrenceRate(round2(recurrenceRate(incidents)))
                .availability(round2(availability(mtbf, mttr)))
                .topRecurringEquipments(topRecurringEquipments(incidents))
                .build();
    }

    // ── Canonical operational cost subtotal (normalised) ──────────────────────

    /** Canonical operational cost subtotal for a site/month, normalised to the reporting currency. */
    public OperationalCostBreakdown operationalCost(Long siteId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());
        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(siteId, toDate(start), toDate(end));
        List<Maintenance> maintenances = maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(
                siteId, toDate(start), toDate(end));
        return operationalCost(siteId, year, month, incidents, maintenances);
    }

    private OperationalCostBreakdown operationalCost(Long siteId, int year, int month,
                                                    List<Incident> incidents, List<Maintenance> maintenances) {
        String siteCurrency = sitesRepository.findById(siteId)
                .map(Sites::getCurrencyCode)
                .orElse(null);

        // Costs for a single site/month are all in one currency (there is no per-record currency on
        // Incident/Maintenance), so converting each subtotal once at the period's own month is exact.
        ReportingAmount incidentReal = new ReportingAmount()
                .add(currencyConverter.convert(totalIncidentCost(incidents), siteCurrency, year, month), year, month);
        ReportingAmount realisedMaint = new ReportingAmount()
                .add(currencyConverter.convert(realisedMaintenanceCost(maintenances), siteCurrency, year, month), year, month);
        ReportingAmount plannedMaint = new ReportingAmount()
                .add(currencyConverter.convert(plannedMaintenanceCost(maintenances), siteCurrency, year, month), year, month);

        ReportingAmount all = new ReportingAmount().merge(incidentReal).merge(realisedMaint).merge(plannedMaint);
        double operationalCost = round2(incidentReal.total() + realisedMaint.total());

        return new OperationalCostBreakdown(
                incidentReal.total(), realisedMaint.total(), plannedMaint.total(), operationalCost,
                currencyConverter.reportingCurrency(), all.complete(), all.gaps());
    }

    // ── Public primitive calculators (currency-agnostic; reused by equipment-level callers) ──

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

    /** Raw Σ {@code Incident.costReal} (site-local currency). */
    public double totalIncidentCost(List<Incident> incidents) {
        return incidents.stream()
                .filter(i -> i.getCostReal() != null)
                .mapToDouble(Incident::getCostReal)
                .sum();
    }

    /** Raw realised maintenance cost = DONE maintenances that carry a real cost (site-local currency). */
    public double realisedMaintenanceCost(List<Maintenance> maintenances) {
        return maintenances.stream()
                .filter(m -> m.getStatusMaintenance() == StatusMaintenace.DONE && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal)
                .sum();
    }

    /**
     * Raw planned (not-yet-realised) maintenance cost = maintenances that carry a cost and whose
     * status is PLANNED or IN_PROGRESS (site-local currency).
     *
     * <p>IN_PROGRESS is folded in here as "committed but not realised" — it is future spend, so it
     * must not land in {@code operationalCost}/{@code realisedMaintenanceCost}. Previously
     * IN_PROGRESS was silently dropped by every calculator.
     */
    public double plannedMaintenanceCost(List<Maintenance> maintenances) {
        return maintenances.stream()
                .filter(m -> (m.getStatusMaintenance() == StatusMaintenace.PLANNED
                        || m.getStatusMaintenance() == StatusMaintenace.IN_PROGRESS)
                        && m.getCostReal() != null)
                .mapToDouble(Maintenance::getCostReal)
                .sum();
    }

    /** Canonical raw operational cost subtotal = incident real + realised maintenance (site-local currency). */
    public double operationalCost(List<Incident> incidents, List<Maintenance> maintenances) {
        return totalIncidentCost(incidents) + realisedMaintenanceCost(maintenances);
    }

    /**
     * Raw (site-local) operational cost per calendar month of a year — index 1..12.
     * {@code operationalCost = Σ Incident.costReal + Σ DONE Maintenance.costReal} in each month.
     * Consumed by the Financial layer to build a {@code totalRealCost}-based cost trend.
     */
    public double[] rawOperationalCostByMonth(Long siteId, int year) {
        double[] perMonth = new double[13];
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        for (Incident i : incidentRepository.findBySitesIdSiteAndDateBetween(siteId, toDate(start), toDate(end))) {
            if (i.getCostReal() != null && i.getDate() != null) {
                perMonth[monthOf(i.getDate())] += i.getCostReal();
            }
        }
        for (Maintenance m : maintenanceRepository.findByEquipementSiteIdSiteAndDateBetween(
                siteId, toDate(start), toDate(end))) {
            if (m.getStatusMaintenance() == StatusMaintenace.DONE && m.getCostReal() != null && m.getDate() != null) {
                perMonth[monthOf(m.getDate())] += m.getCostReal();
            }
        }
        return perMonth;
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

    private int monthOf(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().getMonthValue();
    }
}
