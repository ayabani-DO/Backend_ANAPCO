package tn.esprit.examen.nomPrenomClasseExamen.market.client;

import tn.esprit.examen.nomPrenomClasseExamen.entities.OilPriceRecord;

import java.time.LocalDate;
import java.util.List;

public interface OilPriceClient {

    OilPriceRecord fetchLatestPrice();

    List<OilPriceRecord> fetchPriceRange(LocalDate start, LocalDate end);
}
