package tn.esprit.examen.nomPrenomClasseExamen.weather.entities;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_data",
        uniqueConstraints = @UniqueConstraint(name = "uq_weather_data_site_date_type", columnNames = {"site_id", "data_date", "source_type"}),
        indexes = @Index(name = "idx_weather_data_site_id", columnList = "site_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Sites site;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WeatherSourceType sourceType;

    @Column(nullable = false)
    private LocalDate dataDate;

    private Double temperatureC;

    private Double windSpeedKmh;

    private Double precipitationMm;

    private Double visibilityKm;

    private Double humidityPercent;

    private Integer weatherCode;

    @Column(nullable = false)
    private LocalDateTime fetchedAt;

    @PrePersist
    public void prePersist() {
        if (this.fetchedAt == null) {
            this.fetchedAt = LocalDateTime.now();
        }
    }
}
