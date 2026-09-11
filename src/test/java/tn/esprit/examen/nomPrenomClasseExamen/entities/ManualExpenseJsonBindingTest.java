package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure Jackson (no Spring context) binding tests for {@link ManualExpense} — FIX 1: the Site
 * relation was previously fully hidden with {@code @JsonIgnore}; it now mirrors the bounded
 * {@code @JsonIgnoreProperties} strategy already used by Incident/Maintenance/Equipement, so
 * requests can bind it and responses can expose it without recursing back into the site's own
 * collections.
 */
class ManualExpenseJsonBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_siteReference_populatesSiteId() throws Exception {
        String json = """
                {
                  "category": "TRAVEL",
                  "amount": 300.0,
                  "currencyCode": "EUR",
                  "site": { "idSite": 3 }
                }
                """;

        ManualExpense expense = mapper.readValue(json, ManualExpense.class);

        assertThat(expense.getSite()).isNotNull();
        assertThat(expense.getSite().getIdSite()).isEqualTo(3L);
        assertThat(expense.getAmount()).isEqualTo(300.0);
    }

    @Test
    void serialize_exposesSiteWithoutRecursiveBackReference() throws Exception {
        Sites site = new Sites();
        site.setIdSite(3L);
        site.setCodeRef("TN-PLANT-2");

        ManualExpense expense = new ManualExpense();
        expense.setId(1L);
        expense.setCategory(ManualExpenseCategory.TRAVEL);
        expense.setAmount(300.0);
        expense.setCurrencyCode("EUR");
        expense.setSite(site);

        // Back-reference populated on purpose: it must not leak into the JSON.
        site.setManualExpenses(Set.of(expense));

        String out = mapper.writeValueAsString(expense);
        JsonNode root = mapper.readTree(out); // parses => finite, no infinite recursion

        assertThat(out).doesNotContain("\"manualExpenses\"");
        assertThat(root.path("site").path("idSite").asLong()).isEqualTo(3L);
        assertThat(root.path("site").path("codeRef").asText()).isEqualTo("TN-PLANT-2");
        assertThat(root.path("site").has("manualExpenses")).isFalse();
    }
}
