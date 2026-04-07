package tn.esprit.examen.nomPrenomClasseExamen.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@ToString
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CountryCurrencyRef {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCountry;

    private String ref;

}
