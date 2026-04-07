package tn.esprit.examen.nomPrenomClasseExamen.market.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.examen.nomPrenomClasseExamen.entities.MonthlyFeatureSnapshot;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MonthlyFeatureAggregationService;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EnergyPriceRecordRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MonthlyFeatureSnapshotRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.OilPriceRecordRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/ml/features")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "ML Features", description = "Monthly feature snapshot computation and training data export")
public class FeatureSnapshotController {

    private final MonthlyFeatureAggregationService aggregationService;
    private final MonthlyFeatureSnapshotRepository snapshotRepository;
    private final SitesRepository sitesRepository;
    private final OilPriceRecordRepository oilPriceRecordRepository;
    private final EnergyPriceRecordRepository energyPriceRecordRepository;

    @PostMapping("/compute/{year}/{month}")
    @Operation(summary = "Compute monthly feature snapshots for all active sites")
    public Map<String, Object> compute(@PathVariable int year, @PathVariable int month) {
        List<MonthlyFeatureSnapshot> snapshots = aggregationService.computeAllSites(year, month);
        return Map.of("status", "OK", "sitesComputed", snapshots.size(), "year", year, "month", month);
    }

    @PostMapping("/backfill-targets")
    @Operation(summary = "Backfill nextMonthTotalCostEur targets across all snapshots")
    public Map<String, String> backfillTargets() {
        aggregationService.backfillTargets();
        return Map.of("status", "OK", "message", "Target backfill completed");
    }

    @PostMapping("/enrich-market-prices")
    @Operation(summary = "Update oil/gas/electricity columns on all existing snapshots from the price tables (no recompute)")
    public Map<String, Object> enrichMarketPrices() {
        List<MonthlyFeatureSnapshot> all = snapshotRepository.findAllByOrderByYearDescMonthDesc();
        int updated = 0;

        for (MonthlyFeatureSnapshot snap : all) {
            LocalDate monthStart = LocalDate.of(snap.getYear(), snap.getMonth(), 1);
            LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());

            Double oilAvg = oilPriceRecordRepository.findAvgPriceBetween(monthStart, monthEnd).orElse(null);

            String countryCode = (snap.getSite() != null && snap.getSite().getCountryCode() != null)
                    ? snap.getSite().getCountryCode() : "DE";
            Double gasAvg = energyPriceRecordRepository
                    .findAvgGasPriceBetween(countryCode, monthStart, monthEnd).orElse(null);
            Double elecAvg = energyPriceRecordRepository
                    .findAvgElectricityPriceBetween(countryCode, monthStart, monthEnd).orElse(null);

            boolean changed = false;
            if (oilAvg != null && !oilAvg.equals(snap.getOilPriceAvgUsd())) {
                snap.setOilPriceAvgUsd(Math.round(oilAvg * 100.0) / 100.0);
                changed = true;
            }
            if (gasAvg != null && !gasAvg.equals(snap.getGasPriceAvgEurMwh())) {
                snap.setGasPriceAvgEurMwh(Math.round(gasAvg * 100.0) / 100.0);
                changed = true;
            }
            if (elecAvg != null && !elecAvg.equals(snap.getElectricityPriceAvgEurMwh())) {
                snap.setElectricityPriceAvgEurMwh(Math.round(elecAvg * 100.0) / 100.0);
                changed = true;
            }

            if (changed) {
                snapshotRepository.save(snap);
                updated++;
            }
        }

        log.info("Market price enrichment: {} snapshots updated out of {}", updated, all.size());
        return Map.of("status", "OK", "totalSnapshots", all.size(), "updatedSnapshots", updated);
    }

    @GetMapping("/training-data/cost-forecast")
    @Operation(summary = "Export training data for Model 1 (cost forecast) — rows with nextMonthTotalCostEur filled")
    public List<Map<String, Object>> exportCostForecastData() {
        return snapshotRepository.findAllWithCostTarget().stream()
                .map(this::toFeatureMap)
                .toList();
    }

    @GetMapping("/training-data/risk-classification")
    @Operation(summary = "Export training data for Model 2 (risk classification) — rows with riskClass filled")
    public List<Map<String, Object>> exportRiskClassificationData() {
        return snapshotRepository.findAllWithRiskTarget().stream()
                .map(this::toFeatureMap)
                .toList();
    }

    @GetMapping("/all")
    @Operation(summary = "Export all snapshots")
    public List<Map<String, Object>> exportAll() {
        return snapshotRepository.findAllByOrderByYearDescMonthDesc().stream()
                .map(this::toFeatureMap)
                .toList();
    }

    @GetMapping("/site/{siteId}")
    @Operation(summary = "Export snapshots for a specific site")
    public List<Map<String, Object>> exportBySite(@PathVariable Long siteId) {
        return snapshotRepository.findBySiteIdSiteOrderByYearDescMonthDesc(siteId).stream()
                .map(this::toFeatureMap)
                .toList();
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import training data from a CSV file (Tonic Fabricate format) into the monthly_feature_snapshot table")
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Map.of("status", "ERROR", "message", "File is empty");
        }

        // Pre-load all sites into a map
        Map<Long, Sites> siteMap = new HashMap<>();
        sitesRepository.findAll().forEach(s -> siteMap.put(s.getIdSite(), s));

        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return Map.of("status", "ERROR", "message", "CSV is empty");
            }
            String[] headers = headerLine.split(",");

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                try {
                    String[] vals = line.split(",", -1);
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length && i < vals.length; i++) {
                        row.put(headers[i].trim(), vals[i].trim());
                    }

                    Long siteId = Long.parseLong(row.get("site_id"));
                    int year = Integer.parseInt(row.get("year"));
                    int month = Integer.parseInt(row.get("month"));

                    // Skip if already exists
                    if (snapshotRepository.findBySiteIdSiteAndYearAndMonth(siteId, year, month).isPresent()) {
                        skipped++;
                        continue;
                    }

                    // Resolve or auto-create site
                    Sites site = siteMap.get(siteId);
                    if (site == null) {
                        site = new Sites();
                        site.setNom("Site-" + siteId);
                        site.setCodeRef("SITE-" + String.format("%03d", siteId));
                        site = sitesRepository.save(site);
                        siteMap.put(site.getIdSite(), site);
                        log.info("Auto-created site: id={}, nom={}", site.getIdSite(), site.getNom());
                    }

                    MonthlyFeatureSnapshot snap = MonthlyFeatureSnapshot.builder()
                            .site(site)
                            .year(year)
                            .month(month)
                            .totalCostEur(parseDouble(row.get("total_cost_eur")))
                            .previousMonthTotalCostEur(parseDouble(row.get("previous_month_total_cost_eur")))
                            .incidentCostEur(parseDouble(row.get("incident_cost_eur")))
                            .maintenanceCostEur(parseDouble(row.get("maintenance_cost_eur")))
                            .manualExpenseEur(parseDouble(row.get("manual_expense_eur")))
                            .budgetEur(parseDouble(row.get("budget_eur")))
                            .budgetVariancePct(parseDouble(row.get("budget_variance_pct")))
                            .incidentCount(parseInt(row.get("incident_count")))
                            .criticalIncidentCount(parseInt(row.get("critical_incident_count")))
                            .highIncidentCount(parseInt(row.get("high_incident_count")))
                            .avgIncidentSeverity(parseDouble(row.get("avg_incident_severity")))
                            .preventiveMaintenanceCount(parseInt(row.get("preventive_maintenance_count")))
                            .correctiveMaintenanceCount(parseInt(row.get("corrective_maintenance_count")))
                            .inspectionCount(parseInt(row.get("inspection_count")))
                            .correctivePreventiveRatio(parseDouble(row.get("corrective_preventive_ratio")))
                            .avgMtbf(parseDouble(row.get("avg_mtbf")))
                            .avgMttr(parseDouble(row.get("avg_mttr")))
                            .oilPriceAvgUsd(parseDouble(row.get("oil_price_avg_usd")))
                            .gasPriceAvgEurMwh(parseDouble(row.get("gas_price_avg_eur_mwh")))
                            .electricityPriceAvgEurMwh(parseDouble(row.get("electricity_price_avg_eur_mwh")))
                            .weatherRiskScoreAvg(parseDouble(row.get("weather_risk_score_avg")))
                            .weatherAlertCount(parseInt(row.get("weather_alert_count")))
                            .siteType(row.get("site_type"))
                            .equipmentCount(parseInt(row.get("equipment_count")))
                            .dominantEquipmentCategory(row.get("dominant_equipment_category"))
                            .season(parseInt(row.get("season")))
                            .nextMonthTotalCostEur(parseDouble(row.get("next_month_total_cost_eur")))
                            .riskClass(row.getOrDefault("risk_class", null))
                            .computedAt(parseDateTime(row.get("computed_at")))
                            .build();

                    snapshotRepository.save(snap);
                    imported++;
                } catch (Exception e) {
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            return Map.of("status", "ERROR", "message", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors.size());
        if (!errors.isEmpty()) {
            result.put("errorDetails", errors.subList(0, Math.min(errors.size(), 20)));
        }
        return result;
    }

    private Double parseDouble(String val) {
        if (val == null || val.isEmpty()) return null;
        return Double.parseDouble(val);
    }

    private Integer parseInt(String val) {
        if (val == null || val.isEmpty()) return null;
        return (int) Double.parseDouble(val);
    }

    private LocalDateTime parseDateTime(String val) {
        if (val == null || val.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(val, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * Converts a snapshot entity to a flat map suitable for JSON/CSV export to the Python ML service.
     * Column order matches the ML model feature schema.
     */
    private Map<String, Object> toFeatureMap(MonthlyFeatureSnapshot s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("site_id", s.getSite().getIdSite());
        row.put("year", s.getYear());
        row.put("month", s.getMonth());

        // Cost features
        row.put("total_cost_eur", s.getTotalCostEur());
        row.put("previous_month_total_cost_eur", s.getPreviousMonthTotalCostEur());
        row.put("incident_cost_eur", s.getIncidentCostEur());
        row.put("maintenance_cost_eur", s.getMaintenanceCostEur());
        row.put("manual_expense_eur", s.getManualExpenseEur());
        row.put("budget_eur", s.getBudgetEur());
        row.put("budget_variance_pct", s.getBudgetVariancePct());

        // Incident features
        row.put("incident_count", s.getIncidentCount());
        row.put("critical_incident_count", s.getCriticalIncidentCount());
        row.put("high_incident_count", s.getHighIncidentCount());
        row.put("avg_incident_severity", s.getAvgIncidentSeverity());

        // Maintenance features
        row.put("preventive_maintenance_count", s.getPreventiveMaintenanceCount());
        row.put("corrective_maintenance_count", s.getCorrectiveMaintenanceCount());
        row.put("inspection_count", s.getInspectionCount());
        row.put("corrective_preventive_ratio", s.getCorrectivePreventiveRatio());

        // Reliability features
        row.put("avg_mtbf", s.getAvgMtbf());
        row.put("avg_mttr", s.getAvgMttr());

        // External market features
        row.put("oil_price_avg_usd", s.getOilPriceAvgUsd());
        row.put("gas_price_avg_eur_mwh", s.getGasPriceAvgEurMwh());
        row.put("electricity_price_avg_eur_mwh", s.getElectricityPriceAvgEurMwh());

        // Weather features
        row.put("weather_risk_score_avg", s.getWeatherRiskScoreAvg());
        row.put("weather_alert_count", s.getWeatherAlertCount());

        // Categorical features
        row.put("site_type", s.getSiteType());
        row.put("equipment_count", s.getEquipmentCount());
        row.put("dominant_equipment_category", s.getDominantEquipmentCategory());
        row.put("season", s.getSeason());

        // Target columns
        row.put("next_month_total_cost_eur", s.getNextMonthTotalCostEur());
        row.put("risk_class", s.getRiskClass());

        return row;
    }
}
