package tn.esprit.examen.nomPrenomClasseExamen.market.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EnergyPriceRecord;
import tn.esprit.examen.nomPrenomClasseExamen.entities.OilPriceRecord;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EnergyPriceRecordRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.OilPriceRecordRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports historical market data from CSV files.
 *
 * Expected CSV formats:
 *
 * Oil prices (Brent):
 *   date,price
 *   2020-01-02,66.25
 *
 * Energy prices:
 *   date,country_code,gas_price_eur_mwh,electricity_price_eur_mwh
 *   2020-01-02,DE,15.30,42.50
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class HistoricalDataImportService {

    private static final String SOURCE = "HISTORICAL_CSV";

    private final OilPriceRecordRepository oilPriceRecordRepository;
    private final EnergyPriceRecordRepository energyPriceRecordRepository;

    public int importOilPricesCsv(MultipartFile file) {
        List<OilPriceRecord> records = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                try {
                    LocalDate date = parseDate(parts[0].trim());
                    Double price = Double.parseDouble(parts[1].trim());

                    if (oilPriceRecordRepository.findByPriceDate(date).isEmpty()) {
                        records.add(OilPriceRecord.builder()
                                .priceDate(date)
                                .priceUsd(price)
                                .source(SOURCE)
                                .fetchedAt(now)
                                .build());
                    }
                } catch (Exception e) {
                    log.debug("Skipping malformed oil CSV line: {}", line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read oil price CSV: " + e.getMessage(), e);
        }

        oilPriceRecordRepository.saveAll(records);
        log.info("Oil price CSV import complete: {} records saved", records.size());
        return records.size();
    }

    public int importEnergyPricesCsv(MultipartFile file) {
        List<EnergyPriceRecord> records = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) continue;

                try {
                    LocalDate date = parseDate(parts[0].trim());
                    String countryCode = parts[1].trim().toUpperCase();
                    Double gasPrice = parseDoubleOrNull(parts[2].trim());
                    Double elecPrice = parseDoubleOrNull(parts[3].trim());

                    if (energyPriceRecordRepository.findByCountryCodeAndPriceDate(countryCode, date).isEmpty()) {
                        records.add(EnergyPriceRecord.builder()
                                .priceDate(date)
                                .countryCode(countryCode)
                                .gasPriceEurMwh(gasPrice)
                                .electricityPriceEurMwh(elecPrice)
                                .source(SOURCE)
                                .fetchedAt(now)
                                .build());
                    }
                } catch (Exception e) {
                    log.debug("Skipping malformed energy CSV line: {}", line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read energy price CSV: " + e.getMessage(), e);
        }

        energyPriceRecordRepository.saveAll(records);
        log.info("Energy price CSV import complete: {} records saved", records.size());
        return records.size();
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter fmt : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"))) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new DateTimeParseException("Unsupported date format", dateStr, 0);
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null") || value.equals("-")) {
            return null;
        }
        return Double.parseDouble(value);
    }
}
