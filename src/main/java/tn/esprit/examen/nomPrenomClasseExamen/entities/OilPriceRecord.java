package tn.esprit.examen.nomPrenomClasseExamen.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "oil_price_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_oil_price_date", columnNames = {"price_date"}),
        indexes = @Index(name = "idx_oil_price_date", columnList = "price_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OilPriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false)
    private Double priceUsd; // Brent crude USD per barrel

    @Column(nullable = false)
    private String source; // "EIA_API", "HISTORICAL_CSV"

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    public void prePersist() {
        if (this.fetchedAt == null) {
            this.fetchedAt = LocalDateTime.now();
        }
    }
}
