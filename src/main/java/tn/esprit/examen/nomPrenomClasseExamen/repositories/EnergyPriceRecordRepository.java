package tn.esprit.examen.nomPrenomClasseExamen.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EnergyPriceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnergyPriceRecordRepository extends JpaRepository<EnergyPriceRecord, Long> {

    Optional<EnergyPriceRecord> findByCountryCodeAndPriceDate(String countryCode, LocalDate priceDate);

    List<EnergyPriceRecord> findByCountryCodeAndPriceDateBetweenOrderByPriceDateAsc(String countryCode, LocalDate start, LocalDate end);

    Optional<EnergyPriceRecord> findFirstByCountryCodeOrderByPriceDateDesc(String countryCode);

    @Query("SELECT AVG(e.gasPriceEurMwh) FROM EnergyPriceRecord e WHERE e.countryCode = :countryCode AND e.priceDate BETWEEN :start AND :end")
    Optional<Double> findAvgGasPriceBetween(String countryCode, LocalDate start, LocalDate end);

    @Query("SELECT AVG(e.electricityPriceEurMwh) FROM EnergyPriceRecord e WHERE e.countryCode = :countryCode AND e.priceDate BETWEEN :start AND :end")
    Optional<Double> findAvgElectricityPriceBetween(String countryCode, LocalDate start, LocalDate end);
}
