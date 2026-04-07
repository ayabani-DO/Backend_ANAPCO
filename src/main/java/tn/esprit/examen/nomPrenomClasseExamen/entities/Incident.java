package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@ToString

public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long idInncident;

    @Enumerated(EnumType.STRING)
    private SeverityCode severityCode;
    
    @Enumerated(EnumType.STRING)
    private EtatIncident etatIncident;
    
    @DateTimeFormat
    private Date date;
    
    private String description;
    private Double costEstimated;
    private Double costReal;

    @DateTimeFormat
    private Date closedDate;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "sites_id")
    private Sites sites;

    @ManyToOne
    @JoinColumn(name = "equipement_id", nullable = true)
    private Equipement equipement;
    
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "user_id")
    private User utilisateur; // User who created/is handling the incident

}
