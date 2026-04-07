package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.dto.FinanceKpiDto;
import tn.esprit.examen.nomPrenomClasseExamen.services.FinanceKpiService;

@RestController
@RequestMapping("/api/finance/kpi")
@RequiredArgsConstructor
@Tag(name = "Finance - KPI")
public class FinanceKpiController {

    private final FinanceKpiService financeKpiService;

    @GetMapping("/get")
    public FinanceKpiDto getKpi(
            @RequestParam Long siteId,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        return financeKpiService.getKpi(siteId, year, month);
    }
}
