package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.dto.*;

import jakarta.validation.Valid;
import tn.esprit.examen.nomPrenomClasseExamen.services.IncidentKpiService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/incidents/kpi")
@RequiredArgsConstructor
@Tag(name = "Incident KPI", description = "Advanced incident analytics and KPI endpoints")
@CrossOrigin("*")
public class IncidentKpiController {

    private final IncidentKpiService incidentKpiService;

    // ==================== CORE KPI ENDPOINTS ====================

    @GetMapping("/severity")
    @Operation(summary = "Get severity-based KPI", description = "Analyzes incidents by severity levels with weighted scoring")
    public ResponseEntity<IncidentSeverityKpiDto> getSeverityKpi(
            @Parameter(description = "Site ID to analyze") @RequestParam(required = false) Long siteId,
            @Parameter(description = "Year for analysis") @RequestParam Integer year,
            @Parameter(description = "Month for analysis") @RequestParam Integer month) {
        
        if (siteId == null) {
            return ResponseEntity.badRequest().body(null);
        }
        
        IncidentSeverityKpiDto kpi = incidentKpiService.getSeverityKpi(siteId, year, month);
        return ResponseEntity.ok(kpi);
    }

    @GetMapping("/lifecycle")
    @Operation(summary = "Get lifecycle KPI", description = "Analyzes incident resolution performance and operational efficiency")
    public ResponseEntity<IncidentLifecycleKpiDto> getLifecycleKpi(
            @Parameter(description = "Site ID to analyze") @RequestParam(required = false) Long siteId,
            @Parameter(description = "Year for analysis") @RequestParam Integer year,
            @Parameter(description = "Month for analysis") @RequestParam Integer month) {
        
        if (siteId == null) {
            return ResponseEntity.badRequest().body(null);
        }
        
        IncidentLifecycleKpiDto kpi = incidentKpiService.getLifecycleKpi(siteId, year, month);
        return ResponseEntity.ok(kpi);
    }

    @GetMapping("/cost-analysis")
    @Operation(summary = "Get cost analysis KPI", description = "Analyzes incident costs, variances, and financial performance")
    public ResponseEntity<IncidentCostKpiDto> getCostKpi(
            @Parameter(description = "Site ID to analyze") @RequestParam(required = false) Long siteId,
            @Parameter(description = "Year for analysis") @RequestParam Integer year,
            @Parameter(description = "Month for analysis") @RequestParam Integer month,
            @Parameter(description = "Target currency for conversion") @RequestParam(defaultValue = "EUR") String targetCurrency) {
        
        if (siteId == null) {
            return ResponseEntity.badRequest().body(null);
        }
        
        IncidentCostKpiDto kpi = incidentKpiService.getCostKpi(siteId, year, month, targetCurrency);
        return ResponseEntity.ok(kpi);
    }

    @GetMapping("/recurrence")
    @Operation(summary = "Get recurrence analysis", description = "Analyzes incident patterns and recurrence rates")
    public ResponseEntity<IncidentRecurrenceKpiDto> getRecurrenceKpi(
            @Parameter(description = "Site ID to analyze") @RequestParam(required = false) Long siteId,
            @Parameter(description = "Analysis period in days") @RequestParam(defaultValue = "30") Integer days) {
        
        if (siteId == null) {
            return ResponseEntity.badRequest().body(null);
        }
        
        IncidentRecurrenceKpiDto kpi = incidentKpiService.getRecurrenceKpi(siteId, days);
        return ResponseEntity.ok(kpi);
    }

    // ==================== ADVANCED RISK SCORING ENDPOINTS ====================

    @GetMapping("/risk-score/site/{siteId}")
    @Operation(summary = "Calculate site risk score", description = "AI-powered risk assessment for entire site")
    public ResponseEntity<IncidentRiskScoreDto> getSiteRiskScore(
            @Parameter(description = "Site ID to assess") @PathVariable Long siteId) {
        
        IncidentRiskScoreDto riskScore = incidentKpiService.calculateSiteRiskScore(siteId);
        return ResponseEntity.ok(riskScore);
    }

    @GetMapping("/risk-score/equipment/{equipmentId}")
    @Operation(summary = "Calculate equipment risk score", description = "AI-powered risk assessment for specific equipment")
    public ResponseEntity<IncidentRiskScoreDto> getEquipmentRiskScore(
            @Parameter(description = "Equipment ID to assess") @PathVariable Long equipmentId) {
        
        IncidentRiskScoreDto riskScore = incidentKpiService.calculateEquipmentRiskScore(equipmentId);
        return ResponseEntity.ok(riskScore);
    }

    // ==================== COMBINED ANALYSIS ENDPOINTS ====================

    @GetMapping("/dashboard/{siteId}")
    @Operation(summary = "Get complete dashboard data", description = "Returns all KPIs for comprehensive dashboard view")
    public ResponseEntity<DashboardKpiDto> getDashboardKpi(
            @Parameter(description = "Site ID for dashboard") @PathVariable Long siteId,
            @Parameter(description = "Year for analysis") @RequestParam Integer year,
            @Parameter(description = "Month for analysis") @RequestParam Integer month,
            @Parameter(description = "Target currency") @RequestParam(defaultValue = "EUR") String targetCurrency) {
        
        DashboardKpiDto dashboard = DashboardKpiDto.builder()
            .severityKpi(incidentKpiService.getSeverityKpi(siteId, year, month))
            .lifecycleKpi(incidentKpiService.getLifecycleKpi(siteId, year, month))
            .costKpi(incidentKpiService.getCostKpi(siteId, year, month, targetCurrency))
            .recurrenceKpi(incidentKpiService.getRecurrenceKpi(siteId, 30))
            .siteRiskScore(incidentKpiService.calculateSiteRiskScore(siteId))
            .build();
        
        return ResponseEntity.ok(dashboard);
    }

    // ==================== TREND ANALYSIS ENDPOINTS ====================

    @GetMapping("/trend/{siteId}")
    @Operation(summary = "Get incident trends", description = "Analyzes incident trends over specified period")
    public ResponseEntity<IncidentTrendDto> getIncidentTrend(
            @Parameter(description = "Site ID to analyze") @PathVariable Long siteId,
            @Parameter(description = "Analysis period in months") @RequestParam(defaultValue = "6") Integer months) {
        
        // This would implement trend analysis over multiple months
        // For now, return a basic implementation
        IncidentTrendDto trend = IncidentTrendDto.builder()
            .siteId(siteId)
            .analysisMonths(months)
            .trendDirection("STABLE") // Would calculate actual trend
            .trendPercentage(0.0) // Would calculate actual percentage
            .build();
        
        return ResponseEntity.ok(trend);
    }

    // ==================== COMPARATIVE ANALYSIS ENDPOINTS ====================

    @GetMapping("/compare/sites")
    @Operation(summary = "Compare multiple sites", description = "Compares KPIs across multiple sites")
    public ResponseEntity<SiteComparisonDto> compareSites(
            @Parameter(description = "Site IDs to compare") @RequestParam java.util.List<Long> siteIds,
            @Parameter(description = "Year for analysis") @RequestParam Integer year,
            @Parameter(description = "Month for analysis") @RequestParam Integer month) {
        
        // Implementation would compare multiple sites
        return ResponseEntity.ok(new SiteComparisonDto());
    }
}
