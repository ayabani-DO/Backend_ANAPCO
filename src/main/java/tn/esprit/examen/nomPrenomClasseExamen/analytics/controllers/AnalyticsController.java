package tn.esprit.examen.nomPrenomClasseExamen.analytics.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.EquipmentAnalyticsDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FinancialKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.OperationalKpiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.EquipmentAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.FinancialAnalyticsService;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.services.OperationalAnalyticsService;

import java.util.List;

/**
 * Analytics Layer REST surface. Additive to the existing KPI endpoints — nothing here replaces
 * or renames a legacy route. Read-only, so it is covered by the existing {@code /api/**} GET
 * authorization rule (any authenticated role).
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Centralised analytics layer (single source of truth)")
public class AnalyticsController {

    private final OperationalAnalyticsService operationalAnalyticsService;
    private final FinancialAnalyticsService financialAnalyticsService;
    private final EquipmentAnalyticsService equipmentAnalyticsService;

    @GetMapping("/operational")
    @Operation(summary = "Consolidated operational KPIs (incidents + maintenance) for a site/month")
    public OperationalKpiDTO getOperationalKpi(
            @RequestParam Long siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return operationalAnalyticsService.getOperationalKpi(siteId, year, month);
    }

    @GetMapping("/financial")
    @Operation(summary = "Consolidated financial KPIs (budget vs. real, trend, categories) for a site/month")
    public FinancialKpiDTO getFinancialKpi(
            @RequestParam Long siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return financialAnalyticsService.getFinancialKpi(siteId, year, month);
    }

    @GetMapping("/equipment/ranking")
    @Operation(summary = "Rank equipment by 'cost' (default) or 'risk'")
    public List<EquipmentAnalyticsDTO> getEquipmentRanking(
            @RequestParam(defaultValue = "cost") String by,
            @RequestParam(defaultValue = "10") int limit) {
        return equipmentAnalyticsService.getRanking(by, limit);
    }

    @GetMapping("/equipment/{id}")
    @Operation(summary = "Consolidated equipment health card (cost + incidents + maintenance + RUL)")
    public EquipmentAnalyticsDTO getEquipmentAnalytics(@PathVariable Long id) {
        return equipmentAnalyticsService.getEquipmentAnalytics(id);
    }
}
