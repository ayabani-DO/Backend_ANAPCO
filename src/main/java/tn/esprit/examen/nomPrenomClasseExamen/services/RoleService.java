package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Role getOrCreateRole(String name) {
        String normalized = normalizeRoleName(name);
        return roleRepository.findByName(normalized)
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(normalized)
                            .build();
                    return roleRepository.save(newRole);
                });
    }

    public List<Role> getRoles() {
        return roleRepository.findAll();
    }

    private String normalizeRoleName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be blank");
        }
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
