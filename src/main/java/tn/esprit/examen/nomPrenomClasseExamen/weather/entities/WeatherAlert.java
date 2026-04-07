package tn.esprit.examen.nomPrenomClasseExamen.weather.entities;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;

import java.time.LocalDateTime;

@Entity
@Table(name = "weather_alert",
        indexes = {
                @Index(name = "idx_weather_alert_site_id", columnList = "site_id"),
                @Index(name = "idx_weather_alert_risk_assessment_id", columnList = "risk_assessment_id"),
                @Index(name = "idx_weather_alert_status", columnList = "site_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Sites site;

    @Column(name = "risk_assessment_id")
    private Long riskAssessmentId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime validUntil;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = AlertStatus.OPEN;
        }
    }
}
