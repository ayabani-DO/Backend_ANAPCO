package tn.esprit.examen.nomPrenomClasseExamen.weather.entities;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Sites;

import java.time.LocalDateTime;

@Entity
@Table(name = "weather_recommendation",
        indexes = {
                @Index(name = "idx_recommendation_site_id", columnList = "site_id"),
                @Index(name = "idx_recommendation_risk_assessment_id", columnList = "risk_assessment_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Sites site;

    @Column(name = "risk_assessment_id")
    private Long riskAssessmentId;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 1000)
    private String recommendationText;

    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private Boolean actionable;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.actionable == null) {
            this.actionable = true;
        }
    }
}
