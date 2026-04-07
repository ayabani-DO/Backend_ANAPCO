package tn.esprit.examen.nomPrenomClasseExamen.weather.risk;

import lombok.Builder;
import lombok.Data;
import tn.esprit.examen.nomPrenomClasseExamen.weather.entities.RiskLevel;

import java.util.List;

@Data
@Builder
public class RiskComputationResult {
    private Integer riskScore;
    private RiskLevel riskLevel;
    private List<String> factors;
    private String impactPersonnel;
    private String impactEquipment;
    private String impactMaintenance;
    private String impactTransport;
}
