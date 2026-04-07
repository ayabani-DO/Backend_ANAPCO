package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Maintenance;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.MaintenanceRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class MaintenanceService {

    @Autowired
    MaintenanceRepository maintenanceRepository;

    public List<Maintenance> getAllMaintenance(){
        List l;
        return  l = new ArrayList<>(maintenanceRepository.findAll());
    }
}
