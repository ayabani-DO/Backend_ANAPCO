package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.dto.FinanceKpiDto;
import tn.esprit.examen.nomPrenomClasseExamen.entities.BudgetMonthly;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;
import tn.esprit.examen.nomPrenomClasseExamen.entities.ManualExpense;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.BudgetMonthlyRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.FxRateRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.ManualExpenseRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.SitesRepository;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceKpiService {

    private final SitesRepository sitesRepository;
    private final BudgetMonthlyRepository budgetMonthlyRepository;
    private final ManualExpenseRepository manualExpenseRepository;
    private final FxRateRepository fxRateRepository;
// Always converts to EUR for standardization
    /* Budget:The planned/allocated amount for a site per month,
    Source: BudgetMonthly table - manually entered budget planning
     exp:"We plan to spend €10,000 for Site A in March"
     Purpose: Financial planning and target setting */
    /* Real (Actual) : The actual amount spent for a site per month,
     Source: ManualExpense table - manually entered expenses
     exp:"We spent €10,000 for Site A in March"
     Purpose: Financial tracking and reporting */
    /* Variance: The difference between Budget and Real,
     exp:"€10,000 - €10,000 = €0"
     Purpose: Financial analysis and reporting */
    /* Forecast: The predicted amount for a site per month,
     Source: BudgetMonthly table - manually entered budget planning

    /* Variance Forecast: The difference between Forecast and Real,
     exp:"€10,000 - €10,000 = €0" */


    /**
     * This method is used to get the financial KPI (Key Performance Indicator) for a specific site in a given year and month.
     *
     * @param siteId The ID of the site.
     * @param year   The year for which the KPI is requested.
     * @param month  The month for which the KPI is requested.
     * @return A FinanceKpiDto object containing the budget, actual expenses, variance, forecast, variance forecast, and risk level for the specified site and time period.
     * @throws RuntimeException If the site with the specified ID is not found.
     */
    public FinanceKpiDto getKpi(Long siteId, Integer year, Integer month) {//
        // Get the site with the specified ID
        Sites site = sitesRepository.findById(siteId)
                .orElseThrow(() -> new RuntimeException("Site not found"));
        // Find the budget for the specified site, year, and month
        BudgetMonthly budget = budgetMonthlyRepository
                .findBySite_IdSiteAndYearAndMonth(siteId, year, month)
                .orElse(null);
// Calculate the start and end dates for the specified month
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.with(TemporalAdjusters.lastDayOfMonth());

        // 1. Get ALL expenses for Site X and time period (exp:between March 1-31)
        List<ManualExpense> expenses = manualExpenseRepository.findBySite_IdSiteAndDateBetween(siteId, start, end);
        // Calculate the budget in euros
        double budgetEur = budget == null ? 0d : toEur(siteId, year, month, budget.getAmount(), budget.getCurrencyCode(), site.getCurrencyCode());
        // 2. Convert each expense to EUR and SUM them up
        double realEur = expenses.stream()
                .mapToDouble(e -> toEur(siteId, year, month, e.getAmount(), e.getCurrencyCode(), site.getCurrencyCode()))
                .sum();
        // Calculate the variance between the budget and actual expenses
        double varianceEur = realEur - budgetEur;
        double variancePercent = budgetEur == 0d ? 0d : (varianceEur / budgetEur) * 100d;

        double forecastEur = budgetEur;
        double varianceForecastEur = realEur - forecastEur;
        double varianceForecastPercent = forecastEur == 0d ? 0d : (varianceForecastEur / forecastEur) * 100d;
        // Calculate the risk level based on the variance percentage
        String riskLevel = riskLevel(variancePercent);
        // Return the FinanceKpiDto object with all the calculated values
        return FinanceKpiDto.builder()
                .siteId(site.getIdSite())
                .year(year)
                .month(month)
                .budgetEur(round2(budgetEur))
                .realEur(round2(realEur))
                .varianceEur(round2(varianceEur))
                .variancePercent(round2(variancePercent))
                .forecastEur(round2(forecastEur))
                .varianceForecastEur(round2(varianceForecastEur))
                .varianceForecastPercent(round2(varianceForecastPercent))
                .riskLevel(riskLevel)
                .build();
    }

    private double toEur(Long siteId, Integer year, Integer month, Double amount, String currencyCode, String siteCurrencyCode) {
        if (amount == null) {
            return 0d;
        }

        String effectiveCurrency = (currencyCode == null || currencyCode.isBlank()) ? siteCurrencyCode : currencyCode;
        if (effectiveCurrency == null || effectiveCurrency.isBlank() || "EUR".equalsIgnoreCase(effectiveCurrency)) {
            return amount;
        }

        FxRate fxRate = fxRateRepository.findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
                        year,
                        month,
                        effectiveCurrency,
                        "EUR"
                )
                .orElseThrow(() -> new RuntimeException(
                        "FxRate not found for conversion " + effectiveCurrency + "->EUR (siteId=" + siteId + ", year=" + year + ", month=" + month + ")"
                ));

        if (fxRate.getRate() == null) {
            throw new RuntimeException("FxRate.rate is null for conversion " + effectiveCurrency + "->EUR");
        }

        return amount * fxRate.getRate();
    }
//Called from getKpi() method:
//The risk level helps managers quickly identify sites needing financial attention, regardless of whether they're over or under budget.
    private String riskLevel(double variancePercent) {//variancePercent (difference between real vs budget)
        double abs = Math.abs(variancePercent);
        // The absolute value is needed in case variancePercent is negative
        // (which means the actual amount is higher than the budget amount)
//Thresholds
        if (abs < 5d) {
            return "LOW";
        }
        if (abs < 15d) {
            return "MEDIUM";
        }
        return "HIGH";
        // -> Business Context
        //LOW: Financial performance is predictable and well-controlled
        //MEDIUM: Some deviation from budget - requires monitoring
        //HIGH: Major variance - indicates poor planning or unexpected issues
    }

    private double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
