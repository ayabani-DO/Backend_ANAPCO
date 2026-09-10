package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.entities.User;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;
import tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public void assignRoleToUser(Long idUser, String roleName) {
        // Validate against the four fixed roles BEFORE any database lookup.
        String normalizedRoleName = normalizeRoleName(roleName);

        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Resolve an already-seeded role — never create one.
        Role role = roleRepository.findByName(normalizedRoleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        boolean alreadyAssigned = user.getRoles().stream()
                .anyMatch(existingRole -> existingRole.getName().equalsIgnoreCase(role.getName()));
        if (!alreadyAssigned) {
            user.getRoles().add(role);
            userRepository.save(user);
        } else {
            throw new IllegalArgumentException("User already has this role!");
        }
    }

    public void assignAndReplaceRoleToUser(Long idUser, String roleName) {
        // Validate against the four fixed roles BEFORE any database lookup.
        String normalizedRoleName = normalizeRoleName(roleName);

        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Resolve an already-seeded role — never create one.
        Role role = roleRepository.findByName(normalizedRoleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        // Business guard: replacing all roles with a non-ADMIN role strips ADMIN privileges.
        if (hasAdminRole(user) && !SecurityRoles.ADMIN.equalsIgnoreCase(role.getName())) {
            guardLosingAdmin(user, "remove");
        }

        user.getRoles().clear();
        user.getRoles().add(role);
        userRepository.save(user);
    }

    public void removeRoleFromUser(Long idUser, String roleName) {
        // Validate against the four fixed roles BEFORE any database lookup.
        String normalizedRoleName = normalizeRoleName(roleName);

        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Business guard: never let the system (or the caller) drop the last ADMIN privilege.
        if (SecurityRoles.ADMIN.equals(normalizedRoleName) && hasAdminRole(user)) {
            guardLosingAdmin(user, "remove");
        }

        boolean removed = user.getRoles().removeIf(role -> role.getName().equalsIgnoreCase(normalizedRoleName));
        if (!removed) {
            throw new IllegalArgumentException("User does not have this role");
        }
        userRepository.save(user);
    }

    public void updateFullName(Long idUser, String fullName) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (fullName != null && fullName.trim().contains(" ")) {
            String[] parts = fullName.trim().split(" ", 2);
            user.setFirstName(parts[0]);
            user.setLastName(parts[1]);
            userRepository.save(user);
        } else {
            throw new IllegalArgumentException("Full name must include first and last name separated by space.");
        }
    }

    public void updateDateOfBirth(Long idUser, LocalDate newDateOfBirth) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setDateOfBirth(newDateOfBirth);
        userRepository.save(user);
    }

    public User getProfile(Long idUser) {
        return userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getAllUsersExcept(Long currentUserId) {
        return userRepository.findAllExcept(currentUserId);
    }

    public void DeleteUser(Long idUser) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // Soft delete = disable + lock; same safeguards as a deactivation.
        guardDeactivation(user, "delete");
        user.setEnabled(false);
        user.setAccountLocked(true);
        userRepository.save(user);
    }

    public void banUser(Long idUser, boolean lockStatus) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (lockStatus) {
            guardDeactivation(user, "lock");
        }
        user.setAccountLocked(lockStatus);
        userRepository.save(user);
    }

    public void setAccountEnabled(Long idUser, boolean enabled) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!enabled) {
            guardDeactivation(user, "disable");
        }
        user.setEnabled(enabled);
        if (enabled) {
            user.setAccountLocked(false);
        }
        userRepository.save(user);
    }

    /**
     * Normalises and validates a requested role name against the four fixed ANAPCO roles
     * ({@link SecurityRoles#ALL}) <b>before</b> any database lookup. An unknown value
     * (legacy {@code USER}/{@code AGENTF}, {@code TEST}, {@code SUPERADMIN}, {@code manager}, …)
     * is rejected with {@link IllegalArgumentException} → HTTP 400. This method never creates a
     * role; assignment must resolve an already-seeded role row.
     */
    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be blank");
        }
        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        if (!SecurityRoles.ALL.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unknown role '" + roleName + "'. Allowed roles: " + SecurityRoles.ALL);
        }
        return normalized;
    }

    // ---------------------------------------------------------------------
    // Business safeguards: prevent self-lockout and keep at least one ADMIN.
    // ---------------------------------------------------------------------

    /**
     * Blocks operations that would strip ADMIN privileges from the caller themselves,
     * or from the last remaining ADMIN account. Thrown as a state conflict (HTTP 409).
     */
    private void guardLosingAdmin(User target, String action) {
        if (isCurrentUser(target)) {
            throw new IllegalStateException("You cannot " + action + " your own ADMIN role");
        }
        if (userRepository.countByRoleName(SecurityRoles.ADMIN) <= 1) {
            throw new IllegalStateException("Cannot " + action + " the ADMIN role of the last ADMIN account");
        }
    }

    /**
     * Blocks deactivation (lock / disable / soft-delete) of the caller themselves,
     * or of the last remaining ADMIN account.
     */
    private void guardDeactivation(User target, String action) {
        if (isCurrentUser(target)) {
            throw new IllegalStateException("You cannot " + action + " your own account");
        }
        if (hasAdminRole(target) && userRepository.countByRoleName(SecurityRoles.ADMIN) <= 1) {
            throw new IllegalStateException("Cannot " + action + " the last ADMIN account");
        }
    }

    private boolean isCurrentUser(User target) {
        String currentEmail = currentUserEmail();
        return currentEmail != null
                && target.getEmail() != null
                && currentEmail.equalsIgnoreCase(target.getEmail());
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getName();
    }

    private boolean hasAdminRole(User user) {
        return user.getRoles().stream()
                .anyMatch(role -> SecurityRoles.ADMIN.equalsIgnoreCase(role.getName()));
    }
}
