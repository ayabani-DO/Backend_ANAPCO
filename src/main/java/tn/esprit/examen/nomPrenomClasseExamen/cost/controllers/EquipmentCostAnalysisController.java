package tn.esprit.examen.nomPrenomClasseExamen.cost.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.CostCategory;
import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.EquipmentCostAnalysisDto;
import tn.esprit.examen.nomPrenomClasseExamen.cost.services.EquipmentCostAnalysisService;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Equipment Cost Analysis", description = "Operational and maintenance cost analysis per equipment")
public class EquipmentCostAnalysisController {

    private final EquipmentCostAnalysisService costAnalysisService;

    @GetMapping("/api/equipment/{id}/cost-analysis")
    @Operation(summary = "Full cost analysis for one equipment",
            description = "Returns total incident cost, maintenance cost breakdown (preventive/corrective/inspection), cost by severity, monthly trend and cost category")
    public EquipmentCostAnalysisDto getCostAnalysis(@PathVariable Long id) {
        return costAnalysisService.computeCostAnalysis(id);
    }

    @GetMapping("/api/sites/{siteId}/equipment-cost-analysis")
    @Operation(summary = "Cost analysis for all equipment in a site",
            description = "Returns cost analysis for every piece of equipment linked to the given site")
    public List<EquipmentCostAnalysisDto> getCostAnalysisBySite(@PathVariable Long siteId) {
        return costAnalysisService.computeCostAnalysisForSite(siteId);
    }

    @GetMapping("/api/equipment/cost/top-expensive")
    @Operation(summary = "Top N most expensive equipment",
            description = "Returns equipment ranked by totalCost descending. Use ?limit=N to control size (default 10)")
    public List<EquipmentCostAnalysisDto> getTopExpensive(
            @RequestParam(defaultValue = "10") int limit) {
        return costAnalysisService.getTopExpensiveEquipment(limit);
    }

    @GetMapping("/api/cost/by-category")
    @Operation(summary = "Equipment grouped by cost category",
            description = "Returns a map of LOW_COST / MEDIUM_COST / HIGH_COST → list of equipment")
    public Map<CostCategory, List<EquipmentCostAnalysisDto>> getByCostCategory() {
        return costAnalysisService.getEquipmentByCostCategory();
    }

    @GetMapping("/api/cost/monthly-trend")
    @Operation(summary = "Global monthly cost trend",
            description = "Returns a sorted map of YYYY-MM → total cost (incidents + maintenance) across all equipment")
    public Map<String, Double> getGlobalMonthlyCostTrend() {
        return costAnalysisService.getGlobalMonthlyCostTrend();
    }
}
