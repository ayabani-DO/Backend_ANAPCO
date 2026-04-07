package tn.esprit.examen.nomPrenomClasseExamen.market.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EnergyPriceRecord;
import tn.esprit.examen.nomPrenomClasseExamen.entities.OilPriceRecord;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.HistoricalDataImportService;
import tn.esprit.examen.nomPrenomClasseExamen.market.services.MarketDataService;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EnergyPriceRecordRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.OilPriceRecordRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "Oil & energy price sync and historical CSV import")
public class MarketDataController {

    private final MarketDataService marketDataService;
    private final HistoricalDataImportService historicalDataImportService;
    private final OilPriceRecordRepository oilPriceRecordRepository;
    private final EnergyPriceRecordRepository energyPriceRecordRepository;

    // ── Oil Price endpoints ────────────────────────────────

    @PostMapping("/oil/sync")
    @Operation(summary = "Sync oil prices from EIA API for a date range")
    public Map<String, Object> syncOilPrices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        marketDataService.syncOilPrices(start, end);
        List<OilPriceRecord> records = oilPriceRecordRepository.findByPriceDateBetweenOrderByPriceDateAsc(start, end);
        return Map.of("status", "OK", "recordsInDb", records.size(), "data", records);
    }

    @PostMapping("/oil/sync-latest")
    @Operation(summary = "Sync latest oil price from EIA API")
    public Map<String, Object> syncLatestOilPrice() {
        marketDataService.syncLatestOilPrice();
        OilPriceRecord latest = oilPriceRecordRepository.findFirstByOrderByPriceDateDesc().orElse(null);
        if (latest != null) {
            return Map.of("status", "OK", "data", latest);
        }
        return Map.of("status", "WARNING", "message", "Sync called but no oil price found in DB. Check server logs for API errors.");
    }

    @GetMapping("/oil/latest")
    @Operation(summary = "Get the most recent oil price from the database")
    public OilPriceRecord getLatestOilPrice() {
        return oilPriceRecordRepository.findFirstByOrderByPriceDateDesc()
                .orElse(null);
    }

    @GetMapping("/oil")
    @Operation(summary = "Get oil prices for a date range")
    public List<OilPriceRecord> getOilPrices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return oilPriceRecordRepository.findByPriceDateBetweenOrderByPriceDateAsc(start, end);
    }

    @PostMapping(value = "/oil/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import historical oil prices from CSV (columns: date,price)")
    public Map<String, Object> importOilCsv(@RequestParam("file") MultipartFile file) {
        int count = historicalDataImportService.importOilPricesCsv(file);
        return Map.of("status", "OK", "recordsSaved", count);
    }

    // ── Energy Price endpoints ─────────────────────────────

    @PostMapping("/energy/sync")
    @Operation(summary = "Sync energy prices from Open-Meteo for a country and date range")
    public Map<String, Object> syncEnergyPrices(
            @RequestParam String countryCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        marketDataService.syncEnergyPrices(countryCode, start, end);
        List<EnergyPriceRecord> records = energyPriceRecordRepository.findByCountryCodeAndPriceDateBetweenOrderByPriceDateAsc(countryCode, start, end);
        return Map.of("status", "OK", "recordsInDb", records.size(), "data", records);
    }

    @PostMapping("/energy/sync-latest")
    @Operation(summary = "Sync latest energy price from Open-Meteo for a country")
    public Map<String, Object> syncLatestEnergyPrice(@RequestParam String countryCode) {
        marketDataService.syncLatestEnergyPrice(countryCode);
        EnergyPriceRecord latest = energyPriceRecordRepository.findFirstByCountryCodeOrderByPriceDateDesc(countryCode).orElse(null);
        if (latest != null) {
            return Map.of("status", "OK", "data", latest);
        }
        return Map.of("status", "WARNING", "message", "Sync called but no energy price found in DB for " + countryCode + ". Check server logs.");
    }

    @GetMapping("/energy/latest")
    @Operation(summary = "Get the most recent energy price for a country")
    public EnergyPriceRecord getLatestEnergyPrice(@RequestParam String countryCode) {
        return energyPriceRecordRepository.findFirstByCountryCodeOrderByPriceDateDesc(countryCode)
                .orElse(null);
    }

    @GetMapping("/energy")
    @Operation(summary = "Get energy prices for a country and date range")
    public List<EnergyPriceRecord> getEnergyPrices(
            @RequestParam String countryCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return energyPriceRecordRepository.findByCountryCodeAndPriceDateBetweenOrderByPriceDateAsc(countryCode, start, end);
    }

    @PostMapping(value = "/energy/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import historical energy prices from CSV (columns: date,country_code,gas_price_eur_mwh,electricity_price_eur_mwh)")
    public Map<String, Object> importEnergyCsv(@RequestParam("file") MultipartFile file) {
        int count = historicalDataImportService.importEnergyPricesCsv(file);
        return Map.of("status", "OK", "recordsSaved", count);
    }
}
