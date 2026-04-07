package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprit.examen.nomPrenomClasseExamen.entities.FxRate;

import java.util.Optional;

@Repository
public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    Optional<FxRate> findByYearAndMonthAndFromCurrencyIgnoreCaseAndToCurrencyIgnoreCase(
            Integer year,
            Integer month,
            String fromCurrency,
            String toCurrency
    );
}
