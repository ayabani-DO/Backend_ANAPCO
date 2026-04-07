package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.services.ManualExpenseService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/manual-expenses")
@RequiredArgsConstructor
@Tag(name = "Finance - ManualExpense")
public class ManualExpenseController {

    private final ManualExpenseService manualExpenseService;

    @PostMapping("/create/{siteId}")
    public ManualExpense create(@RequestBody ManualExpense manualExpense, @PathVariable Long siteId) {
        return manualExpenseService.create(manualExpense, siteId);
    }

    @PutMapping("/update/{id}")
    public ManualExpense update(@PathVariable Long id, @RequestBody ManualExpense manualExpense) {
        return manualExpenseService.update(id, manualExpense);
    }

    @DeleteMapping("/delete/{id}")
    public void delete(@PathVariable Long id) {
        manualExpenseService.delete(id);
    }

    @GetMapping("/getAll")
    public List<ManualExpense> getAll() {
        return manualExpenseService.getAll();
    }

    @GetMapping("/getById/{id}")
    public ManualExpense getById(@PathVariable Long id) {
        return manualExpenseService.getById(id);
    }

    @GetMapping("/getBySite/{siteId}")
    public List<ManualExpense> getBySite(@PathVariable Long siteId) {
        return manualExpenseService.getBySite(siteId);
    }

    @GetMapping("/getBySiteAndPeriod/{siteId}")
    public List<ManualExpense> getBySiteAndPeriod(
            @PathVariable Long siteId,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end
    ) {
        return manualExpenseService.getBySiteAndPeriod(siteId, start, end);
    }
}
