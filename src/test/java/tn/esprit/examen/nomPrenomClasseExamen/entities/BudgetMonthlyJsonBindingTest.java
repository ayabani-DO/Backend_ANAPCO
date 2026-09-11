package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure Jackson (no Spring context) binding tests for {@link BudgetMonthly} — FIX 1: the Site
 * relation was previously fully hidden with {@code @JsonIgnore}; it now mirrors the bounded
 * {@code @JsonIgnoreProperties} strategy already used by Incident/Maintenance/Equipement, so
 * requests can bind it and responses can expose it without recursing back into the site's own
 * collections.
 */
class BudgetMonthlyJsonBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_siteReference_populatesSiteId() throws Exception {
        String json = """
                {
                  "year": 2024,
                  "month": 6,
                  "amount": 1000.0,
                  "currencyCode": "EUR",
                  "site": { "idSite": 3 }
                }
                """;

        BudgetMonthly budget = mapper.readValue(json, BudgetMonthly.class);

        assertThat(budget.getSite()).isNotNull();
        assertThat(budget.getSite().getIdSite()).isEqualTo(3L);
        assertThat(budget.getAmount()).isEqualTo(1000.0);
    }

    @Test
    void serialize_exposesSiteWithoutRecursiveBackReference() throws Exception {
        Sites site = new Sites();
        site.setIdSite(3L);
        site.setCodeRef("TN-PLANT-2");

        BudgetMonthly budget = new BudgetMonthly();
        budget.setId(1L);
        budget.setYear(2024);
        budget.setMonth(6);
        budget.setAmount(1000.0);
        budget.setCurrencyCode("EUR");
        budget.setSite(site);

        // Back-reference populated on purpose: it must not leak into the JSON.
        site.setBudgets(Set.of(budget));

        String out = mapper.writeValueAsString(budget);
        JsonNode root = mapper.readTree(out); // parses => finite, no infinite recursion

        assertThat(out).doesNotContain("\"budgets\"");
        assertThat(root.path("site").path("idSite").asLong()).isEqualTo(3L);
        assertThat(root.path("site").path("codeRef").asText()).isEqualTo("TN-PLANT-2");
        assertThat(root.path("site").has("budgets")).isFalse();
    }
}
