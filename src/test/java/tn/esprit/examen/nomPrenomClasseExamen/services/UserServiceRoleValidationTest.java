package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.entities.User;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;
import tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests (no Spring context, no DB) proving that {@link UserService} role
 * operations validate the requested name against the four fixed ANAPCO roles
 * ({@link SecurityRoles#ALL}) <b>before</b> any database lookup, and that assignment only ever
 * resolves an already-seeded role — it never creates one.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceRoleValidationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @InjectMocks
    private UserService userService;

    private static Role role(String name) {
        return Role.builder().name(name).build();
    }

    private static User bareUser(Long id) {
        return User.builder().idUser(id).email("u" + id + "@anapco.com")
                .roles(new HashSet<>()).build();
    }

    // ---- valid roles: assignment resolves an existing seeded role ----

    @Test
    void admin_canAssign_OPS_MANAGER_toViewer() {
        User viewer = bareUser(10L);
        viewer.getRoles().add(role(SecurityRoles.VIEWER));
        when(userRepository.findById(10L)).thenReturn(Optional.of(viewer));
        when(roleRepository.findByName("OPS_MANAGER")).thenReturn(Optional.of(role("OPS_MANAGER")));

        assertDoesNotThrow(() -> userService.assignRoleToUser(10L, "OPS_MANAGER"));

        // Found an existing role, never created one; VIEWER kept alongside OPS_MANAGER.
        verify(roleRepository).findByName("OPS_MANAGER");
        verify(roleRepository, never()).save(any(Role.class));
        verify(userRepository).save(viewer);
        assertThat(viewer.getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder(SecurityRoles.VIEWER, "OPS_MANAGER");
    }

    @Test
    void admin_canAssign_FINANCE_CONTROLLER_toViewer() {
        User viewer = bareUser(11L);
        viewer.getRoles().add(role(SecurityRoles.VIEWER));
        when(userRepository.findById(11L)).thenReturn(Optional.of(viewer));
        when(roleRepository.findByName("FINANCE_CONTROLLER"))
                .thenReturn(Optional.of(role("FINANCE_CONTROLLER")));

        assertDoesNotThrow(() -> userService.assignRoleToUser(11L, "FINANCE_CONTROLLER"));

        verify(roleRepository).findByName("FINANCE_CONTROLLER");
        verify(roleRepository, never()).save(any(Role.class));
        assertThat(viewer.getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder(SecurityRoles.VIEWER, "FINANCE_CONTROLLER");
    }

    @Test
    void lowercaseValidRole_isNormalisedAndAccepted() {
        User viewer = bareUser(12L);
        when(userRepository.findById(12L)).thenReturn(Optional.of(viewer));
        when(roleRepository.findByName("OPS_MANAGER")).thenReturn(Optional.of(role("OPS_MANAGER")));

        assertDoesNotThrow(() -> userService.assignRoleToUser(12L, "  ops_manager  "));

        verify(roleRepository).findByName("OPS_MANAGER");
    }

    // ---- invalid roles: rejected BEFORE any DB lookup, nothing created ----

    @Test
    void assign_unknownRole_TEST_isRejectedBeforeAnyDbLookup() {
        assertThatThrownBy(() -> userService.assignRoleToUser(1L, "TEST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown role");

        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void assign_legacyRole_USER_isRejected() {
        assertThatThrownBy(() -> userService.assignRoleToUser(1L, "USER"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void assign_legacyRole_AGENTF_isRejected() {
        assertThatThrownBy(() -> userService.assignRoleToUser(1L, "AGENTF"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void assign_arbitraryLowercase_manager_isRejected() {
        assertThatThrownBy(() -> userService.assignRoleToUser(1L, "manager"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void assign_SUPERADMIN_isRejected() {
        assertThatThrownBy(() -> userService.assignRoleToUser(1L, "SUPERADMIN"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void assignAndReplace_unknownRole_isRejectedBeforeAnyDbLookup() {
        assertThatThrownBy(() -> userService.assignAndReplaceRoleToUser(1L, "TEST"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void removeRole_unknownRole_isRejectedBeforeAnyDbLookup() {
        assertThatThrownBy(() -> userService.removeRoleFromUser(1L, "AGENTF"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void assign_blankRole_isRejected() {
        assertThatThrownBy(() -> userService.assignRoleToUser(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository, roleRepository);
    }

    @Test
    void allowList_containsExactlyTheFourFixedRoles() {
        assertThat(SecurityRoles.ALL).containsExactlyInAnyOrder(
                "ADMIN", "OPS_MANAGER", "FINANCE_CONTROLLER", "VIEWER");
    }
}
