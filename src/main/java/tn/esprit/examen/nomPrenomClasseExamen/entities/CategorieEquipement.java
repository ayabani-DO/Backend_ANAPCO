package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.Set;

@Entity
@AllArgsConstructor
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CategorieEquipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCategorie;
    private String nomEquiepment;

    private String description;
    private Date dateCreation;
    
    private Boolean active = true;

    @OneToMany(mappedBy = "categorie", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("categorie")
    private Set<Equipement> equipements;
}
