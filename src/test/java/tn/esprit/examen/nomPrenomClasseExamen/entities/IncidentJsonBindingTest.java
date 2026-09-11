package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure Jackson (no Spring context) binding tests for {@link Incident} — regression cover for the
 * STEP 2B relation-binding fix: {@code sites} must deserialize from {@code {"idSite": <id>}}
 * (mirroring the Maintenance/Equipement fix) while the serialized response stays finite (no
 * {@code sites.lincident} back-reference).
 */
class IncidentJsonBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_sitesReference_populatesSiteId() throws Exception {
        String json = """
                {
                  "date": "2026-09-10",
                  "sites": { "idSite": 3 }
                }
                """;

        Incident incident = mapper.readValue(json, Incident.class);

        assertThat(incident.getSites()).isNotNull();
        assertThat(incident.getSites().getIdSite()).isEqualTo(3L);
    }

    @Test
    void serialize_doesNotRecursivelyExposeSiteIncidents() throws Exception {
        Sites site = new Sites();
        site.setIdSite(3L);
        site.setCodeRef("TN-PLANT-2");

        Incident incident = new Incident();
        incident.setIdInncident(10L);
        incident.setDate(new Date());
        incident.setEtatIncident(EtatIncident.OPEN);
        incident.setSites(site);

        // Back-reference populated on purpose: it must not leak into the JSON.
        site.setLincident(Set.of(incident));

        String out = mapper.writeValueAsString(incident);
        JsonNode root = mapper.readTree(out); // parses => finite, no infinite recursion

        assertThat(out).doesNotContain("\"lincident\"");
        assertThat(root.path("sites").path("idSite").asLong()).isEqualTo(3L);
        assertThat(root.path("sites").path("codeRef").asText()).isEqualTo("TN-PLANT-2");
        assertThat(root.path("sites").has("lincident")).isFalse();
    }
}
