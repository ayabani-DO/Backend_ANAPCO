package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;

import java.util.List;
import java.util.Optional;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    Optional<FxRate> findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
            Integer year,
            Integer month,
            String fromCurrency,
            String toCurrency
    );

    /**
     * Distinct currency codes seen in stored FX rows (both sides), upper-cased. Temporary source for
     * the "convert this record into…" target-currency dropdown until the Currency reference table
     * (Step 1C-B) provides {@code GET /api/finance/currencies}.
     */
    @Query("SELECT DISTINCT UPPER(TRIM(f.fromCurrency)) FROM FxRate f WHERE f.fromCurrency IS NOT NULL AND TRIM(f.fromCurrency) <> ''")
    List<String> findDistinctFromCurrencyCodes();

    @Query("SELECT DISTINCT UPPER(TRIM(f.toCurrency)) FROM FxRate f WHERE f.toCurrency IS NOT NULL AND TRIM(f.toCurrency) <> ''")
    List<String> findDistinctToCurrencyCodes();
}
