package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;
import tn.esprit.examen.nomPrenomClasseExamen.services.FxRateService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/fx-rates")
@RequiredArgsConstructor
@Tag(name = "Finance - FxRate")
public class FxRateController {

    private final FxRateService fxRateService;

    @PostMapping("/create")
    public FxRate create(@RequestBody FxRate fxRate) {
        return fxRateService.create(fxRate);
    }

    @PutMapping("/update/{id}")
    public FxRate update(@PathVariable Long id, @RequestBody FxRate fxRate) {
        return fxRateService.update(id, fxRate);
    }

    @DeleteMapping("/delete/{id}")
    public void delete(@PathVariable Long id) {
        fxRateService.delete(id);
    }

    @GetMapping("/getAll")
    public List<FxRate> getAll() {
        return fxRateService.getAll();
    }

    @GetMapping("/getById/{id}")
    public FxRate getById(@PathVariable Long id) {
        return fxRateService.getById(id);
    }

    @GetMapping("/getByYearMonth")
    public FxRate getByYearMonth(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam String fromCurrency,
            @RequestParam String toCurrency
    ) {
        return fxRateService.getByYearMonthAndPair(year, month, fromCurrency, toCurrency);
    }

    @PostMapping("/fetch/latest")
    public FxRate fetchLatest(@RequestParam String toCurrency) {
        return fxRateService.fetchAndSaveLatestRate(toCurrency);
    }

    @PostMapping("/fetch/historical")
    public FxRate fetchHistorical(
            @RequestParam String toCurrency,
            @RequestParam String date
    ) {
        return fxRateService.fetchAndSaveHistoricalRate(toCurrency, LocalDate.parse(date));
    }

    @GetMapping("/convert")
    public Double convert(
            @RequestParam Double amount,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        return fxRateService.convert(amount, from, to, year, month);
    }
}
