package tn.esprit.examen.nomPrenomClasseExamen.market.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.entities.*;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.*;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.RiskAssessment;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.WeatherAlert;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.RiskAssessmentRepository;
import tn.esprit.examen.nomPrenomClasseExamen.weather.repositories.WeatherAlertRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MonthlyFeatureAggregationService {

    private final SitesRepository sitesRepository;
    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final ManualExpenseRepository manualExpenseRepository;
    private final BudgetMonthlyRepository budgetMonthlyRepository;
    private final EquipementRepository equipementRepository;
    private final FxRateRepository fxRateRepository;
    private final OilPriceRecordRepository oilPriceRecordRepository;
    private final EnergyPriceRecordRepository energyPriceRecordRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final WeatherAlertRepository weatherAlertRepository;
    private final MonthlyFeatureSnapshotRepository snapshotRepository;

    /**
     * Compute and persist a MonthlyFeatureSnapshot for every active site for the given year/month.
     */
    public List<MonthlyFeatureSnapshot> computeAllSites(int year, int month) {
        List<Sites> activeSites = sitesRepository.findAll().stream()
                .filter(s -> s.getStatusSites() == null || s.getStatusSites() == StatusSites.ACTIF)
                .toList();

        List<MonthlyFeatureSnapshot> results = new ArrayList<>();
        for (Sites site : activeSites) {
            MonthlyFeatureSnapshot snapshot = computeForSite(site, year, month);
            results.add(snapshot);
        }

        backfillTargets();
        log.info("Monthly feature aggregation complete for {}-{}: {} site snapshots", year, month, results.size());
        return results;
    }

    /**
     * Compute and persist a snapshot for one site and one month.
     */
    public MonthlyFeatureSnapshot computeForSite(Sites site, int year, int month) {
        // Check if already exists — update instead of duplicate
        MonthlyFeatureSnapshot snapshot = snapshotRepository
                .findBySiteIdSiteAndYearAndMonth(site.getIdSite(), year, month)
                .orElse(MonthlyFeatureSnapshot.builder()
                        .site(site)
                        .year(year)
                        .month(month)
                        .build());

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        Date dateStart = toDate(monthStart);
        Date dateEnd = toDate(monthEnd);
        LocalDateTime dtStart = monthStart.atStartOfDay();
        LocalDateTime dtEnd = monthEnd.atTime(23, 59, 59);

        String siteCurrency = site.getCurrencyCode();

        // ── Incidents ──────────────────────────────────────────
        List<Incident> incidents = incidentRepository.findBySitesIdSiteAndDateBetween(site.getIdSite(), monthStart, monthEnd);

        int incidentCount = incidents.size();
        int criticalCount = (int) incidents.stream().filter(i -> i.getSeverityCode() == SeverityCode.CRITICAL).count();
        int highCount = (int) incidents.stream().filter(i -> i.getSeverityCode() == SeverityCode.HIGH).count();
        double avgSeverity = incidents.stream()
                .filter(i -> i.getSeverityCode() != null)
                .mapToInt(i -> i.getSeverityCode().getWeight())
                .average().orElse(0.0);

        double incidentCostEur = incidents.stream()
                .filter(i -> i.getCostReal() != null)
                .mapToDouble(i -> toEur(site.getIdSite(), year, month, i.getCostReal(), siteCurrency))
                .sum();

        // ── Maintenance ────────────────────────────────────────
        List<Maintenance> maintenances = maintenanceRepository
                .findByEquipementSiteIdSiteAndDateBetween(site.getIdSite(), dateStart, dateEnd);

        int preventiveCount = (int) maintenances.stream().filter(m -> m.getTypeMaintenance() == TypeMaintenance.PREVENTIVE).count();
        int correctiveCount = (int) maintenances.stream().filter(m -> m.getTypeMaintenance() == TypeMaintenance.CORRECTIVE).count();
        int inspectionCount = (int) maintenances.stream().filter(m -> m.getTypeMaintenance() == TypeMaintenance.INSPECTION).count();
        double correctivePreventiveRatio = preventiveCount == 0 ? correctiveCount : (double) correctiveCount / preventiveCount;

        double maintenanceCostEur = maintenances.stream()
                .filter(m -> m.getCostReal() != null)
                .mapToDouble(m -> toEur(site.getIdSite(), year, month, m.getCostReal(), siteCurrency))
                .sum();

        // ── Manual Expenses ────────────────────────────────────
        List<ManualExpense> expenses = manualExpenseRepository.findBySite_IdSiteAndDateBetween(site.getIdSite(), monthStart, monthEnd);
        double manualExpenseEur = expenses.stream()
                .mapToDouble(e -> toEur(site.getIdSite(), year, month, e.getAmount(),
                        e.getCurrencyCode() != null ? e.getCurrencyCode() : siteCurrency))
                .sum();

        // ── Budget ─────────────────────────────────────────────
        BudgetMonthly budget = budgetMonthlyRepository.findBySite_IdSiteAndYearAndMonth(site.getIdSite(), year, month).orElse(null);
        double budgetEur = budget == null ? 0.0 : toEur(site.getIdSite(), year, month, budget.getAmount(),
                budget.getCurrencyCode() != null ? budget.getCurrencyCode() : siteCurrency);

        // ── Total cost & variance ──────────────────────────────
        double totalCostEur = round2(incidentCostEur + maintenanceCostEur + manualExpenseEur);
        double budgetVariancePct = budgetEur == 0 ? 0.0 : round2((totalCostEur - budgetEur) / budgetEur * 100.0);

        // ── Previous month cost ────────────────────────────────
        LocalDate prevMonth = monthStart.minusMonths(1);
        Double previousMonthCost = snapshotRepository
                .findBySiteIdSiteAndYearAndMonth(site.getIdSite(), prevMonth.getYear(), prevMonth.getMonthValue())
                .map(MonthlyFeatureSnapshot::getTotalCostEur)
                .orElse(null);

        // ── MTBF & MTTR (averaged across site equipment) ──────
        List<Equipement> equipment = equipementRepository.findBySiteIdSite(site.getIdSite());
        double avgMtbf = computeSiteAvgMtbf(equipment, monthStart, monthEnd);
        double avgMttr = computeSiteAvgMttr(equipment, monthStart, monthEnd);

        // ── Equipment categorical features ─────────────────────
        int equipmentCount = equipment.size();
        String dominantCategory = equipment.stream()
                .filter(e -> e.getCategorie() != null && e.getCategorie().getNomEquiepment() != null)
                .collect(Collectors.groupingBy(e -> e.getCategorie().getNomEquiepment(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");

        // ── External market data ───────────────────────────────
        Double oilPriceAvg = oilPriceRecordRepository.findAvgPriceBetween(monthStart, monthEnd).orElse(null);
        String countryCode = site.getCountryCode() != null ? site.getCountryCode() : "DE";
        Double gasPriceAvg = energyPriceRecordRepository.findAvgGasPriceBetween(countryCode, monthStart, monthEnd).orElse(null);
        Double elecPriceAvg = energyPriceRecordRepository.findAvgElectricityPriceBetween(countryCode, monthStart, monthEnd).orElse(null);

        // ── Weather features ───────────────────────────────────
        List<RiskAssessment> assessments = riskAssessmentRepository.findBySiteIdSiteAndAssessedAtBetween(site.getIdSite(), dtStart, dtEnd);
        Double weatherRiskAvg = assessments.isEmpty() ? null
                : assessments.stream().mapToInt(RiskAssessment::getRiskScore).average().orElse(0.0);
        List<WeatherAlert> alerts = weatherAlertRepository.findBySiteIdSiteAndCreatedAtBetween(site.getIdSite(), dtStart, dtEnd);
        int weatherAlertCount = alerts.size();

        // ── Categorical / temporal ─────────────────────────────
        String siteType = site.getSiteType() != null ? site.getSiteType().name() : "OTHER";
        int season = toSeason(month);

        // ── Risk class target (rule-based label) ───────────────
        String riskClass = computeRiskClass(budgetVariancePct, criticalCount, correctivePreventiveRatio,
                weatherRiskAvg != null ? weatherRiskAvg : 0.0);

        // ── Populate snapshot ──────────────────────────────────
        snapshot.setTotalCostEur(totalCostEur);
        snapshot.setPreviousMonthTotalCostEur(previousMonthCost);
        snapshot.setIncidentCostEur(round2(incidentCostEur));
        snapshot.setMaintenanceCostEur(round2(maintenanceCostEur));
        snapshot.setManualExpenseEur(round2(manualExpenseEur));
        snapshot.setBudgetEur(round2(budgetEur));
        snapshot.setBudgetVariancePct(budgetVariancePct);
        snapshot.setIncidentCount(incidentCount);
        snapshot.setCriticalIncidentCount(criticalCount);
        snapshot.setHighIncidentCount(highCount);
        snapshot.setAvgIncidentSeverity(round2(avgSeverity));
        snapshot.setPreventiveMaintenanceCount(preventiveCount);
        snapshot.setCorrectiveMaintenanceCount(correctiveCount);
        snapshot.setInspectionCount(inspectionCount);
        snapshot.setCorrectivePreventiveRatio(round2(correctivePreventiveRatio));
        snapshot.setAvgMtbf(round2(avgMtbf));
        snapshot.setAvgMttr(round2(avgMttr));
        snapshot.setOilPriceAvgUsd(oilPriceAvg);
        snapshot.setGasPriceAvgEurMwh(gasPriceAvg);
        snapshot.setElectricityPriceAvgEurMwh(elecPriceAvg);
        snapshot.setWeatherRiskScoreAvg(weatherRiskAvg != null ? round2(weatherRiskAvg) : null);
        snapshot.setWeatherAlertCount(weatherAlertCount);
        snapshot.setSiteType(siteType);
        snapshot.setEquipmentCount(equipmentCount);
        snapshot.setDominantEquipmentCategory(dominantCategory);
        snapshot.setSeason(season);
        snapshot.setRiskClass(riskClass);
        snapshot.setComputedAt(LocalDateTime.now());

        return snapshotRepository.save(snapshot);
    }

    /**
     * Backfill nextMonthTotalCostEur target for all snapshots where it is missing.
     * For each snapshot(site, year, month), set target = totalCostEur of snapshot(site, year, month+1).
     */
    public void backfillTargets() {
        List<MonthlyFeatureSnapshot> all = snapshotRepository.findAllByOrderByYearDescMonthDesc();
        Map<String, MonthlyFeatureSnapshot> lookup = new HashMap<>();
        for (MonthlyFeatureSnapshot s : all) {
            lookup.put(s.getSite().getIdSite() + "-" + s.getYear() + "-" + s.getMonth(), s);
        }

        int filled = 0;
        for (MonthlyFeatureSnapshot s : all) {
            if (s.getNextMonthTotalCostEur() != null) continue;

            LocalDate nextMonth = LocalDate.of(s.getYear(), s.getMonth(), 1).plusMonths(1);
            String key = s.getSite().getIdSite() + "-" + nextMonth.getYear() + "-" + nextMonth.getMonthValue();
            MonthlyFeatureSnapshot next = lookup.get(key);

            if (next != null && next.getTotalCostEur() != null) {
                s.setNextMonthTotalCostEur(next.getTotalCostEur());
                snapshotRepository.save(s);
                filled++;
            }
        }
        if (filled > 0) {
            log.info("Backfilled nextMonthTotalCostEur for {} snapshots", filled);
        }
    }

    /**
     * Update oil/gas/electricity columns on all existing snapshots from the price tables (no recompute).
     * Returns the number of snapshots updated.
     */
    public int enrichMarketPrices() {
        List<MonthlyFeatureSnapshot> all = snapshotRepository.findAllByOrderByYearDescMonthDesc();
        int updated = 0;

        for (MonthlyFeatureSnapshot snap : all) {
            LocalDate monthStart = LocalDate.of(snap.getYear(), snap.getMonth(), 1);
            LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());

            Double oilAvg = oilPriceRecordRepository.findAvgPriceBetween(monthStart, monthEnd).orElse(null);
            String countryCode = (snap.getSite() != null && snap.getSite().getCountryCode() != null)
                    ? snap.getSite().getCountryCode() : "DE";
            Double gasAvg = energyPriceRecordRepository.findAvgGasPriceBetween(countryCode, monthStart, monthEnd).orElse(null);
            Double elecAvg = energyPriceRecordRepository.findAvgElectricityPriceBetween(countryCode, monthStart, monthEnd).orElse(null);

            boolean changed = false;
            if (oilAvg != null && !oilAvg.equals(snap.getOilPriceAvgUsd())) {
                snap.setOilPriceAvgUsd(round2(oilAvg));
                changed = true;
            }
            if (gasAvg != null && !gasAvg.equals(snap.getGasPriceAvgEurMwh())) {
                snap.setGasPriceAvgEurMwh(round2(gasAvg));
                changed = true;
            }
            if (elecAvg != null && !elecAvg.equals(snap.getElectricityPriceAvgEurMwh())) {
                snap.setElectricityPriceAvgEurMwh(round2(elecAvg));
                changed = true;
            }
            if (changed) {
                snapshotRepository.save(snap);
                updated++;
            }
        }
        log.info("Market price enrichment: {} snapshots updated out of {}", updated, all.size());
        return updated;
    }

    // ── Helper methods ─────────────────────────────────────

    private double computeSiteAvgMtbf(List<Equipement> equipment, LocalDate start, LocalDate end) {
        if (equipment.isEmpty()) return 0.0;
        double totalMtbf = 0;
        int count = 0;
        int days = (int) (end.toEpochDay() - start.toEpochDay()) + 1;

        for (Equipement eq : equipment) {
            List<Incident> eqIncidents = incidentRepository
                    .findByEquipementIdEquipementAndDateBetween(eq.getIdEquipement(), start, end);
            double mtbf = eqIncidents.isEmpty() ? days : (double) days / eqIncidents.size();
            totalMtbf += mtbf;
            count++;
        }
        return count == 0 ? 0.0 : totalMtbf / count;
    }

    private double computeSiteAvgMttr(List<Equipement> equipment, LocalDate start, LocalDate end) {
        if (equipment.isEmpty()) return 0.0;
        List<Double> resolutionDays = new ArrayList<>();

        for (Equipement eq : equipment) {
            List<Incident> eqIncidents = incidentRepository
                    .findByEquipementIdEquipementAndDateBetween(eq.getIdEquipement(), start, end);
            for (Incident inc : eqIncidents) {
                if (inc.getEtatIncident() == EtatIncident.CLOSED && inc.getDate() != null && inc.getClosedDate() != null) {
                    long millis = inc.getClosedDate().getTime() - inc.getDate().getTime();
                    resolutionDays.add(millis / (1000.0 * 60 * 60 * 24));
                }
            }
        }
        return resolutionDays.isEmpty() ? 0.0 : resolutionDays.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String computeRiskClass(double budgetVariancePct, int criticalCount, double correctivePreventiveRatio, double weatherRiskAvg) {
        double absVariance = Math.abs(budgetVariancePct);
        if (absVariance > 15 || criticalCount >= 2 || correctivePreventiveRatio > 3.0 || weatherRiskAvg >= 70) {
            return "HIGH_RISK";
        }
        if (absVariance > 5 || criticalCount >= 1 || correctivePreventiveRatio > 1.5 || weatherRiskAvg >= 40) {
            return "MEDIUM_RISK";
        }
        return "LOW_RISK";
    }

    private int toSeason(int month) {
        return switch (month) {
            case 12, 1, 2 -> 1;  // Winter
            case 3, 4, 5 -> 2;   // Spring
            case 6, 7, 8 -> 3;   // Summer
            case 9, 10, 11 -> 4; // Autumn
            default -> 0;
        };
    }

    private double toEur(Long siteId, int year, int month, Double amount, String currencyCode) {
        if (amount == null) return 0.0;
        if (currencyCode == null || currencyCode.isBlank() || "EUR".equalsIgnoreCase(currencyCode)) {
            return amount;
        }
        try {
            FxRate rate = fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                    year, month, currencyCode, "EUR").orElse(null);
            if (rate != null && rate.getRate() != null) {
                return amount * rate.getRate();
            }
        } catch (Exception e) {
            log.warn("FxRate not found for {} -> EUR (site={}, {}-{}). Using raw amount.", currencyCode, siteId, year, month);
        }
        return amount;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
