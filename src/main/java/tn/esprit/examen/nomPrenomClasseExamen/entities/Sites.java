package tn.esprit.examen.nomPrenomClasseExamen.entities;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.SiteType;

import java.util.Set;

@Entity
@AllArgsConstructor
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Sites {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSite;

    private String codeRef;// try to make it create tout seul (ex : UK-PLANT-1, DE-PLANT-1)
    private String nom;
    private Double latitude;
    private Double longitude;
    private String countryCode;
    private String currencyCode;
    @Enumerated(EnumType.STRING)
    private StatusSites statusSites;

    @Enumerated(EnumType.STRING)
    private SiteType siteType;

    @OneToMany(mappedBy = "sites", cascade = CascadeType.ALL)
    private Set<Incident> lincident;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private Set<Equipement> equipements;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private Set<BudgetMonthly> budgets;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private Set<ManualExpense> manualExpenses;

/*
👉 À l’écran, ton OPS peut filtrer :

            “Montre-moi tous les incidents sur les Pipelines du site UK-PLANT-1”.
*/

}
