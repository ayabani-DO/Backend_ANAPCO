package tn.esprit.examen.nomPrenomClasseExamen.decision.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.ForecastDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.dto.GlobalRiskDTO;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.ForecastEngine;
import tn.esprit.examen.nomPrenomClasseExamen.decision.services.RiskEngine;

/**
 * Decision Layer REST surface. Additive and read-only, covered by the existing {@code /api/**} GET
 * authorization rule. Nothing here replaces the legacy risk/RUL endpoints.
 */
@RestController
@RequestMapping("/api/decision")
@RequiredArgsConstructor
@Tag(name = "Decision", description = "Decision layer: global risk and cost forecast over the analytics KPIs")
public class DecisionController {

    private final RiskEngine riskEngine;
    private final ForecastEngine forecastEngine;

    @GetMapping("/risk/global")
    @Operation(summary = "Global risk (operational + financial + weather + ML) for a site/month")
    public GlobalRiskDTO globalRisk(
            @RequestParam Long siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return riskEngine.assessGlobalRisk(siteId, year, month);
    }

    @GetMapping("/forecast")
    @Operation(summary = "Short-term cost forecast derived from the analytics KPIs")
    public ForecastDTO forecast(
            @RequestParam Long siteId,
            @RequestParam int year,
            @RequestParam int month) {
        return forecastEngine.forecast(siteId, year, month);
    }
}
