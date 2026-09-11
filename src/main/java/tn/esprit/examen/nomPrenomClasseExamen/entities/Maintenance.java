package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Entity
@AllArgsConstructor
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Maintenance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMaintenance;


    private String refCode;

    @Enumerated(EnumType.STRING)
    private TypeMaintenance typeMaintenance;

    @Enumerated(EnumType.STRING)
    private StatusMaintenace statusMaintenance;

    @DateTimeFormat
    private Date date;
    
    private String description;
    
    private Double costReal;


    // Bound the relation (both directions): the client sends {"idEquipement": <id>} on
    // create/update and the service resolves the managed Equipement server-side; on response the
    // equipment's own back-reference collection is omitted so the JSON stays finite.
    @ManyToOne
    @JsonIgnoreProperties("maintenances")
    @JoinColumn(name = "equipement_id")
    private Equipement equipement;

}
