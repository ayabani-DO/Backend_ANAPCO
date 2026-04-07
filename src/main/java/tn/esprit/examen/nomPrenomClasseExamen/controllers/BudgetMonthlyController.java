package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.services.BudgetMonthlyService;

import java.util.List;

@RestController
@RequestMapping("/api/finance/budgets")
@RequiredArgsConstructor
@Tag(name = "Finance - BudgetMonthly")
public class BudgetMonthlyController {

    private final BudgetMonthlyService budgetMonthlyService;

    @PostMapping("/create/{siteId}")
    public BudgetMonthly create(@RequestBody BudgetMonthly budgetMonthly, @PathVariable Long siteId) {
        return budgetMonthlyService.create(budgetMonthly, siteId);
    }

    @PutMapping("/update/{id}")
    public BudgetMonthly update(@PathVariable Long id, @RequestBody BudgetMonthly budgetMonthly) {
        return budgetMonthlyService.update(id, budgetMonthly);
    }

    @DeleteMapping("/delete/{id}")
    public void delete(@PathVariable Long id) {
        budgetMonthlyService.delete(id);
    }

    @GetMapping("/getAll")
    public List<BudgetMonthly> getAll() {
        return budgetMonthlyService.getAll();
    }

    @GetMapping("/getById/{id}")
    public BudgetMonthly getById(@PathVariable Long id) {
        return budgetMonthlyService.getById(id);
    }

    @GetMapping("/getBySite/{siteId}")
    public List<BudgetMonthly> getBySite(@PathVariable Long siteId) {
        return budgetMonthlyService.getBySite(siteId);
    }
}
