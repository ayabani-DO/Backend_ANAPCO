package tn.esprit.examen.nomPrenomClasseExamen.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@AllArgsConstructor
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idFxRate;

    private Integer year;

    private Integer month;

    private String fromCurrency;

    private String toCurrency;

    private Double rate;

}
