package tn.esprit.examen.nomPrenomClasseExamen.market.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.entities.EnergyPriceRecord;
import tn.esprit.examen.nomPrenomClasseExamen.entities.OilPriceRecord;
import tn.esprit.examen.nomPrenomClasseExamen.market.client.EnergyPriceClient;
import tn.esprit.examen.nomPrenomClasseExamen.market.client.OilPriceClient;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EnergyPriceRecordRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.OilPriceRecordRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MarketDataServiceImpl implements MarketDataService {

    private final OilPriceClient oilPriceClient;
    private final EnergyPriceClient energyPriceClient;
    private final OilPriceRecordRepository oilPriceRecordRepository;
    private final EnergyPriceRecordRepository energyPriceRecordRepository;

    @Override
    public void syncOilPrices(LocalDate start, LocalDate end) {
        List<OilPriceRecord> fetched = oilPriceClient.fetchPriceRange(start, end);
        int saved = 0;
        for (OilPriceRecord record : fetched) {
            if (oilPriceRecordRepository.findByPriceDate(record.getPriceDate()).isEmpty()) {
                oilPriceRecordRepository.save(record);
                saved++;
            }
        }
        log.info("Oil price sync complete: {} fetched, {} new records saved ({} to {})", fetched.size(), saved, start, end);
    }

    @Override
    public void syncEnergyPrices(String countryCode, LocalDate start, LocalDate end) {
        List<EnergyPriceRecord> fetched = energyPriceClient.fetchPriceRange(countryCode, start, end);
        int saved = 0;
        for (EnergyPriceRecord record : fetched) {
            if (energyPriceRecordRepository.findByCountryCodeAndPriceDate(record.getCountryCode(), record.getPriceDate()).isEmpty()) {
                energyPriceRecordRepository.save(record);
                saved++;
            }
        }
        log.info("Energy price sync complete for {}: {} fetched, {} new records saved ({} to {})", countryCode, fetched.size(), saved, start, end);
    }

    @Override
    public void syncLatestOilPrice() {
        OilPriceRecord latest = oilPriceClient.fetchLatestPrice();
        if (latest != null && oilPriceRecordRepository.findByPriceDate(latest.getPriceDate()).isEmpty()) {
            oilPriceRecordRepository.save(latest);
            log.info("Latest oil price saved: {} USD on {}", latest.getPriceUsd(), latest.getPriceDate());
        }
    }

    @Override
    public void syncLatestEnergyPrice(String countryCode) {
        EnergyPriceRecord latest = energyPriceClient.fetchLatestPrice(countryCode);
        if (latest != null && energyPriceRecordRepository.findByCountryCodeAndPriceDate(latest.getCountryCode(), latest.getPriceDate()).isEmpty()) {
            energyPriceRecordRepository.save(latest);
            log.info("Latest energy price saved for {}: elec={} gas={} EUR/MWh on {}",
                    countryCode, latest.getElectricityPriceEurMwh(), latest.getGasPriceEurMwh(), latest.getPriceDate());
        }
    }
}
