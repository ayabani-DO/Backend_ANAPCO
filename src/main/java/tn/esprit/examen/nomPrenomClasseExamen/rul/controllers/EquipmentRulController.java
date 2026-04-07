package tn.esprit.examen.nomPrenomClasseExamen.rul.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.rul.dto.EquipmentRulDto;
import tn.esprit.examen.nomPrenomClasseExamen.rul.services.EquipmentRulService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Equipment RUL", description = "Remaining Useful Life estimation for equipment")
public class EquipmentRulController {

    private final EquipmentRulService equipmentRulService;

    @GetMapping("/api/equipment/{id}/rul")
    @Operation(summary = "Get RUL for a specific equipment",
            description = "Computes degradation score, RUL category, estimated remaining days, MTBF, MTTR for one equipment")
    public EquipmentRulDto getRul(@PathVariable Long id) {
        return equipmentRulService.computeRul(id);
    }

    @GetMapping("/api/sites/{siteId}/equipment-rul")
    @Operation(summary = "Get RUL for all equipment in a site",
            description = "Returns RUL analysis for every piece of equipment linked to the given site")
    public List<EquipmentRulDto> getRulBySite(@PathVariable Long siteId) {
        return equipmentRulService.computeRulForSite(siteId);
    }

    @GetMapping("/api/equipment/rul/high-risk")
    @Operation(summary = "Get all high-risk equipment (RUL_SHORT)",
            description = "Returns all equipment across all sites with RUL category = RUL_SHORT (score 60-100)")
    public List<EquipmentRulDto> getHighRiskEquipment() {
        return equipmentRulService.getHighRiskEquipment();
    }
}
