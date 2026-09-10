package tn.esprit.examen.nomPrenomClasseExamen.controllers;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.User;
import tn.esprit.examen.nomPrenomClasseExamen.services.UserService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('ADMIN')")
public class UserController {

    /** The only role names accepted by the assignment endpoints; anything else returns 400. */
    private static final String ALLOWED_ROLES_DOC =
            "One of the four fixed ANAPCO roles: ADMIN, OPS_MANAGER, FINANCE_CONTROLLER, VIEWER. "
                    + "Any other value returns 400. Roles are never created via the API.";

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/getAll")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping("/{idUser}/assign-role")
    public ResponseEntity<String> assignRoleToUser(
            @PathVariable Long idUser,
            @Parameter(description = ALLOWED_ROLES_DOC) @RequestParam String roleName) {
        try {
            userService.assignRoleToUser(idUser, roleName);
            return ResponseEntity.ok("Role '" + roleName + "' assigned to user successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/getUserById/{idUser}")
    public User getProfile(@PathVariable Long idUser) {
        return userService.getProfile(idUser);
    }

    @GetMapping("/all-except-me")
    public List<User> getAllUsersExceptMe(@RequestParam Long currentUserId) {
        return userService.getAllUsersExcept(currentUserId);
    }

    @PostMapping("/{idUser}/assignAndReplaceRoleToUser")
    public ResponseEntity<String> assignAndReplaceRoleToUser(
            @PathVariable Long idUser,
            @Parameter(description = ALLOWED_ROLES_DOC) @RequestParam String roleName) {
        try {
            userService.assignAndReplaceRoleToUser(idUser, roleName);
            return ResponseEntity.ok("Role '" + roleName + "' assigned to user successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{idUser}/roles/{roleName}")
    public ResponseEntity<String> removeRoleFromUser(
            @PathVariable Long idUser,
            @Parameter(description = ALLOWED_ROLES_DOC) @PathVariable String roleName) {
        try {
            userService.removeRoleFromUser(idUser, roleName);
            return ResponseEntity.ok("Role '" + roleName + "' removed from user successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping("/{idUser}/ban")
    public ResponseEntity<String> banUser(@PathVariable Long idUser, @RequestParam boolean lockStatus) {
        userService.banUser(idUser, lockStatus);
        return ResponseEntity.ok("User account lock status updated");
    }

    @PutMapping("/{idUser}/enabled")
    public ResponseEntity<String> setAccountEnabled(@PathVariable Long idUser, @RequestParam boolean enabled) {
        userService.setAccountEnabled(idUser, enabled);
        return ResponseEntity.ok("User account enabled status updated");
    }

    @PutMapping("/{idUser}/updateFullName")
    public ResponseEntity<Map<String, String>> updateFullName(@PathVariable Long idUser, @RequestParam String fullName) {
        try {
            userService.updateFullName(idUser, fullName);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Full name updated successfully.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PutMapping("/{idUser}/updateDateOfBirth")
    public ResponseEntity<Map<String, String>> updateDateOfBirth(@PathVariable Long idUser, @RequestParam String newDateOfBirth) {
        try {
            LocalDate dateOfBirth = LocalDate.parse(newDateOfBirth);
            userService.updateDateOfBirth(idUser, dateOfBirth);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Date of birth updated successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Invalid date format.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
