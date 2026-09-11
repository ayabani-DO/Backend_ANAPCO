package tn.esprit.examen.nomPrenomClasseExamen.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure Jackson (no Spring context) binding tests for {@link Maintenance} — regression cover for the
 * request-binding fix: {@code equipement} must deserialize from {@code {"idEquipement": <id>}} while
 * the serialized response stays finite (no {@code equipement.maintenances} back-reference).
 */
class MaintenanceJsonBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserialize_equipementReference_populatesEquipmentId() throws Exception {
        String json = """
                {
                  "typeMaintenance": "PREVENTIVE",
                  "statusMaintenance": "PLANNED",
                  "date": "2026-09-10",
                  "costReal": 100,
                  "equipement": { "idEquipement": 8 }
                }
                """;

        Maintenance m = mapper.readValue(json, Maintenance.class);

        assertThat(m.getEquipement()).isNotNull();
        assertThat(m.getEquipement().getIdEquipement()).isEqualTo(8L);
        assertThat(m.getTypeMaintenance()).isEqualTo(TypeMaintenance.PREVENTIVE);
        assertThat(m.getStatusMaintenance()).isEqualTo(StatusMaintenace.PLANNED);
        assertThat(m.getDate()).isNotNull();
        assertThat(m.getCostReal()).isEqualTo(100.0);
    }

    @Test
    void deserialize_ignoresUnknownEquipmentBackReference() throws Exception {
        String json = """
                {
                  "equipement": {
                    "idEquipement": 8,
                    "maintenances": [ { "idMaintenance": 1 } ]
                  }
                }
                """;

        Maintenance m = mapper.readValue(json, Maintenance.class);

        assertThat(m.getEquipement().getIdEquipement()).isEqualTo(8L);
        assertThat(m.getEquipement().getMaintenances()).isNullOrEmpty();
    }

    @Test
    void serialize_doesNotRecursivelyExposeEquipmentMaintenances() throws Exception {
        Sites site = new Sites();
        site.setIdSite(3L);
        site.setCodeRef("TN-PLANT-2");

        Equipement equipement = new Equipement();
        equipement.setIdEquipement(8L);
        equipement.setRefEquipement("TN-PLANT-2-EQ-001");
        equipement.setSite(site);

        Maintenance m = new Maintenance();
        m.setIdMaintenance(1L);
        m.setTypeMaintenance(TypeMaintenance.PREVENTIVE);
        m.setStatusMaintenance(StatusMaintenace.PLANNED);
        m.setDate(new Date());
        m.setCostReal(100.0);
        m.setEquipement(equipement);

        // Back-reference populated on purpose: it must not leak into the JSON.
        equipement.setMaintenances(Set.of(m));

        String out = mapper.writeValueAsString(m);
        JsonNode root = mapper.readTree(out); // parses => finite, no infinite recursion

        assertThat(out).doesNotContain("\"maintenances\"");
        assertThat(root.path("equipement").path("idEquipement").asLong()).isEqualTo(8L);
        assertThat(root.path("equipement").path("refEquipement").asText()).isEqualTo("TN-PLANT-2-EQ-001");
        assertThat(root.path("equipement").has("maintenances")).isFalse();
        assertThat(root.path("equipement").path("site").path("codeRef").asText()).isEqualTo("TN-PLANT-2");
    }
}
