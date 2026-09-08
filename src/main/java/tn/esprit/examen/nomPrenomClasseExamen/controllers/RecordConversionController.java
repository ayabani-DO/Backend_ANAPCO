package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.examen.nomPrenomClasseExamen.dto.ConvertedMoneyDto;
import tn.esprit.examen.nomPrenomClasseExamen.services.RecordConversionService;

import java.util.List;

/**
 * Record-scoped, display-only currency conversion. The client passes only a stored record's id and
 * a target currency; the backend derives amount, source currency and business month from the record.
 * Nothing is persisted. Full paths are declared per method so each route sits under the base path of
 * the record it converts.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Currency conversion (record-scoped, display-only)")
public class RecordConversionController {

    private final RecordConversionService recordConversionService;

    @GetMapping("/api/finance/budgets/{id}/amount-in/{targetCurrency}")
    @Operation(summary = "Convert a BudgetMonthly amount into another currency (display only)")
    public ConvertedMoneyDto convertBudget(@PathVariable Long id, @PathVariable String targetCurrency) {
        return recordConversionService.convertBudget(id, targetCurrency);
    }

    @GetMapping("/api/finance/manual-expenses/{id}/amount-in/{targetCurrency}")
    @Operation(summary = "Convert a ManualExpense amount into another currency (display only)")
    public ConvertedMoneyDto convertManualExpense(@PathVariable Long id, @PathVariable String targetCurrency) {
        return recordConversionService.convertManualExpense(id, targetCurrency);
    }

    @GetMapping("/api/analytics/incidents/{id}/cost-in/{targetCurrency}")
    @Operation(summary = "Convert an Incident cost (real|estimated) into another currency (display only)")
    public ConvertedMoneyDto convertIncidentCost(@PathVariable Long id,
                                                 @PathVariable String targetCurrency,
                                                 @RequestParam(defaultValue = "real") String field) {
        return recordConversionService.convertIncidentCost(id, targetCurrency, field);
    }

    @GetMapping("/api/analytics/maintenances/{id}/cost-in/{targetCurrency}")
    @Operation(summary = "Convert a Maintenance cost into another currency (display only)")
    public ConvertedMoneyDto convertMaintenanceCost(@PathVariable Long id, @PathVariable String targetCurrency) {
        return recordConversionService.convertMaintenanceCost(id, targetCurrency);
    }

    @GetMapping("/api/finance/currencies/supported")
    @Operation(summary = "TEMPORARY: currencies that can be used as a conversion target "
            + "(distinct FxRate codes + reporting currency). Step 1C-B replaces this with GET /api/finance/currencies.")
    public List<String> supportedTargetCurrencies() {
        return recordConversionService.supportedTargetCurrencies();
    }
}
