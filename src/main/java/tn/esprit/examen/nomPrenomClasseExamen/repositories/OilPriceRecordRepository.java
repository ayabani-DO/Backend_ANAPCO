package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.examen.nomPrenomClasseExamen.entities.OilPriceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OilPriceRecordRepository extends JpaRepository<OilPriceRecord, Long> {

    Optional<OilPriceRecord> findByPriceDate(LocalDate priceDate);

    List<OilPriceRecord> findByPriceDateBetweenOrderByPriceDateAsc(LocalDate start, LocalDate end);

    Optional<OilPriceRecord> findFirstByOrderByPriceDateDesc();

    @Query("SELECT AVG(o.priceUsd) FROM OilPriceRecord o WHERE o.priceDate BETWEEN :start AND :end")
    Optional<Double> findAvgPriceBetween(LocalDate start, LocalDate end);
}
