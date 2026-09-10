package tn.esprit.examen.nomPrenomClasseExamen.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString


public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String token;

    /**
     * Token purpose (Part 2B). Added as a <b>nullable</b> column ({@code token_type}); Hibernate
     * {@code ddl-auto=update} adds it additively on the next start. A {@code null} value is a
     * pre-2B legacy token and is rejected by both the activation and reset flows.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", length = 32)
    private TokenType type;

    private LocalDateTime createdAt;
    private LocalDateTime validatedAt;
    private LocalDateTime expiresAt;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private User user;

}