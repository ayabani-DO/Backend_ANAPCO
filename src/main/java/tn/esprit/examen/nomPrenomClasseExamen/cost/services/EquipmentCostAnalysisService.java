package tn.esprit.examen.nomPrenomClasseExamen.cost.services;

import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.CostCategory;
import tn.esprit.examen.nomPrenomClasseExamen.cost.dto.EquipmentCostAnalysisDto;

import java.util.List;
import java.util.Map;

public interface EquipmentCostAnalysisService {

    EquipmentCostAnalysisDto computeCostAnalysis(Long equipmentId);

    List<EquipmentCostAnalysisDto> computeCostAnalysisForSite(Long siteId);

    List<EquipmentCostAnalysisDto> getTopExpensiveEquipment(int limit);

    Map<CostCategory, List<EquipmentCostAnalysisDto>> getEquipmentByCostCategory();

    Map<String, Double> getGlobalMonthlyCostTrend();
}
