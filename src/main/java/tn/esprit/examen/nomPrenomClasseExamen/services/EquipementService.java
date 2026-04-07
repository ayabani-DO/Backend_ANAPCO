package tn.esprit.examen.nomPrenomClasseExamen.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Equipement;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.EquipementRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipementService {

    private final EquipementRepository equipementRepo;


    public Equipement createEquipement(Equipement equipement) {
        return equipementRepo.save(equipement);
    }


    public Equipement updateEquipement(Long id, Equipement equipement) {
        Equipement e = equipementRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Equipement non trouvé"));
        e.setNomEquipement(equipement.getNomEquipement());
        e.setRefEquipement(equipement.getRefEquipement());
        e.setSerialNumber(equipement.getSerialNumber());
        e.setStatusEquipement(equipement.getStatusEquipement());
        return equipementRepo.save(e);
    }


    public void deleteEquipement(Long id) {
        equipementRepo.deleteById(id);
    }


    public List<Equipement> getAllEquipements() {
        return equipementRepo.findAll();
    }


    public Equipement getEquipementById(Long id) {
        return equipementRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Equipement non trouvé"));
    }
}
