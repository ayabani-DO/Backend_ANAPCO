package tn.esprit.examen.nomPrenomClasseExamen.controllers;


import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.services.RoleService;

import java.util.List;

/**
 * Read-only view of the four fixed ANAPCO business roles. Role definitions are fixed system
 * reference data seeded once on startup — they are never created through the API. ADMIN manages
 * <i>role assignments</i> to users via {@code /users/**}, not role definitions here.
 */
@RestController
@RequestMapping("/roles")
@PreAuthorize("hasAuthority('ADMIN')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/getRoles")
    public List<Role> getRoles() {
        return roleService.getRoles();
    }
}
