package tn.esprit.examen.nomPrenomClasseExamen.market.client;

import tn.esprit.examen.nomPrenomClasseExamen.entities.EnergyPriceRecord;

import java.time.LocalDate;
import java.util.List;

public interface EnergyPriceClient {

    EnergyPriceRecord fetchLatestPrice(String countryCode);

    List<EnergyPriceRecord> fetchPriceRange(String countryCode, LocalDate start, LocalDate end);
}
