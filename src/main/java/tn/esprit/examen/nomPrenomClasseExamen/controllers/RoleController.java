package tn.esprit.examen.nomPrenomClasseExamen.controllers;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.services.RoleService;

import java.util.List;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("/add")
    public ResponseEntity<String> addRole(@RequestParam String name) {
        try {
            Role newRole = roleService.getOrCreateRole(name);
            return ResponseEntity.status(HttpStatus.CREATED).body("Role '" + newRole.getName() + "' added successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/getRoles")
    public List<Role> getRoles() {
        return roleService.getRoles();
    }
}
