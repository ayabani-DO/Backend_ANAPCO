package tn.esprit.examen.nomPrenomClasseExamen.weather.entities;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_assessment",
        uniqueConstraints = @UniqueConstraint(name = "uq_risk_assessment_weather_data", columnNames = {"weather_data_id"}),
        indexes = {
                @Index(name = "idx_risk_assessment_site_id", columnList = "site_id"),
                @Index(name = "idx_risk_assessment_weather_data_id", columnList = "weather_data_id"),
                @Index(name = "idx_risk_assessment_assessed_at", columnList = "site_id, assessed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Sites site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weather_data_id")
    private WeatherData weatherData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SiteType siteType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SensitivityLevel sensitivityLevel;

    @Column(length = 1200)
    private String riskFactors;

    @Column(length = 800)
    private String impactPersonnel;

    @Column(length = 800)
    private String impactEquipment;

    @Column(length = 800)
    private String impactMaintenance;

    @Column(length = 800)
    private String impactTransport;

    @Column(length = 64)
    private String engineType;

    @Column(length = 32)
    private String engineVersion;

    @Column(nullable = false)
    private LocalDateTime assessedAt;

    @PrePersist
    public void prePersist() {
        if (this.assessedAt == null) {
            this.assessedAt = LocalDateTime.now();
        }
    }
}
