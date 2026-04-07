package tn.esprit.examen.nomPrenomClasseExamen.rul.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class EquipmentRulDto {
    private Long equipmentId;
    private String equipmentName;
    private String equipmentRef;
    private int rulScore;
    private RulCategory rulCategory;
    private int estimatedRemainingDays;
    private double mtbf;
    private double mttr;
    private int recentIncidentCount;
    private int recentCorrectiveMaintenanceCount;
    private double recentAverageCost;
    private List<String> mainFactors;
    private String recommendedAction;
    private LocalDate analysisDate;
}
