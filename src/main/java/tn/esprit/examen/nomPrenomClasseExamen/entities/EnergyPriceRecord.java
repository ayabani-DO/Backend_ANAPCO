package tn.esprit.examen.nomPrenomClasseExamen.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "energy_price_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_energy_price_country_date", columnNames = {"country_code", "price_date"}),
        indexes = {
                @Index(name = "idx_energy_price_date", columnList = "price_date"),
                @Index(name = "idx_energy_price_country", columnList = "country_code")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnergyPriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "country_code", nullable = false, length = 5)
    private String countryCode; // "UK", "DE", "FR" — matches Sites.countryCode

    private Double gasPriceEurMwh; // natural gas €/MWh

    private Double electricityPriceEurMwh; // electricity €/MWh

    @Column(nullable = false)
    private String source; // "ENTSOE_API", "HISTORICAL_CSV"

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    public void prePersist() {
        if (this.fetchedAt == null) {
            this.fetchedAt = LocalDateTime.now();
        }
    }
}
