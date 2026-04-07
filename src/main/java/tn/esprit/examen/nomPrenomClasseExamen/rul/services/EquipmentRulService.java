package tn.esprit.examen.nomPrenomClasseExamen.rul.services;

import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.EquipmentRulDto;

import java.util.List;

public interface EquipmentRulService {

    EquipmentRulDto computeRul(Long equipmentId);

    List<EquipmentRulDto> computeRulForSite(Long siteId);

    List<EquipmentRulDto> getHighRiskEquipment();
}
