package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.Set;
import java.util.UUID;

@Entity
@AllArgsConstructor
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Equipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEquipement;

    private String nomEquipement;
    private String refEquipement;
    @Enumerated(EnumType.STRING)
    private StatusEquipement statusEquipement;

    private String serialNumber; // try to make it cree tout suel

    @ManyToOne
    @JsonIgnoreProperties("equipements")
    @JoinColumn(name = "categorie_id")
    private CategorieEquipement categorie;  // lien vers catégorie


    @ManyToOne
    @JoinColumn(name = "site_id")
    @JsonIgnoreProperties({"lincident", "equipements", "budgets", "manualExpenses"})
    private Sites site;

    @OneToMany(mappedBy = "equipement", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<Maintenance> maintenances;

    @PrePersist
    public void generateSerialNumber() {
        if (serialNumber == null || serialNumber.isEmpty()) {
            serialNumber = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
    }
}
