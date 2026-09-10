package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;

import java.util.List;

/**
 * Read-only access to the fixed ANAPCO role reference data. Role definitions are seeded once on
 * startup ({@code CommandLineRunner}) and are never created at runtime — there is deliberately no
 * create/get-or-create method here.
 */
@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<Role> getRoles() {
        return roleRepository.findAll();
    }
}
