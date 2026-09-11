package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@AllArgsConstructor
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ManualExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private ManualExpenseCategory category;

    private Double amount;

    private String currencyCode;

    // Bound the relation (both directions): the client sends {"site": {"idSite": <id>}} on
    // update (create still takes siteId as a path variable) and the service resolves the managed
    // Site server-side; on response the site's own back-reference collections are omitted so the
    // JSON stays finite (mirrors Incident/Equipement.site).
    @ManyToOne
    @JsonIgnoreProperties({"lincident", "equipements", "budgets", "manualExpenses"})
    @JoinColumn(name = "site_id")
    private Sites site;
}
