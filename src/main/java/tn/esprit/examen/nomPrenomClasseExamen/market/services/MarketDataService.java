package tn.esprit.examen.nomPrenomClasseExamen.market.services;

import java.time.LocalDate;

public interface MarketDataService {

    void syncOilPrices(LocalDate start, LocalDate end);

    void syncEnergyPrices(String countryCode, LocalDate start, LocalDate end);

    void syncLatestOilPrice();

    void syncLatestEnergyPrice(String countryCode);
}
