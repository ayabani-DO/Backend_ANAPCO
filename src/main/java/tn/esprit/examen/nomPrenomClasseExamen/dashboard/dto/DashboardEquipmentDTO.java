package tn.esprit.examen.nomPrenomClasseExamen.dashboard.dto;

import lombok.Builder;
import lombok.Data;
import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.EquipmentAnalyticsDTO;

/**
 * Business equipment view: the consolidated equipment health card plus a decision-oriented
 * attention flag and the priority action to take.
 */
@Data
@Builder
public class DashboardEquipmentDTO {

    private EquipmentAnalyticsDTO equipment;

    /** True when the equipment needs attention (high risk or degraded health). */
    private boolean attentionRequired;

    /** The single most important action, surfaced from the RUL recommendation. */
    private String priorityAction;
}
