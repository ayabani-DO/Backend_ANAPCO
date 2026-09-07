package tn.esprit.examen.nomPrenomClasseExamen.dashboard.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardAiDTO;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardEquipmentDTO;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardFinancialDTO;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto.DashboardOverviewDTO;
import tn.esprit.examen.nomPrenomClasseExamen.dashboard.services.DashboardService;

/**
 * Dashboard layer REST surface — a small set of consolidated, business-oriented views for the
 * frontend. Additive and read-only (covered by the existing {@code /api/**} GET authorization rule);
 * nothing here replaces or renames a legacy endpoint.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Consolidated business views orchestrating analytics, decision, AI and weather")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Site overview: operational + financial + global risk + weather")
    public DashboardOverviewDTO overview(
            @RequestParam Long siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return dashboardService.getOverview(siteId, year, month);
    }

    @GetMapping("/financial")
    @Operation(summary = "Financial view: KPIs + forecast + financial risk level")
    public DashboardFinancialDTO financial(
            @RequestParam Long siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return dashboardService.getFinancial(siteId, year, month);
    }

    @GetMapping("/equipment/{id}")
    @Operation(summary = "Equipment health card with attention flag and priority action")
    public DashboardEquipmentDTO equipment(@PathVariable Long id) {
        return dashboardService.getEquipment(id);
    }

    @GetMapping("/ai")
    @Operation(summary = "AI view: ML cost/risk predictions with a rule-based forecast fallback")
    public DashboardAiDTO ai(
            @RequestParam Long siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return dashboardService.getAi(siteId, year, month);
    }
}
