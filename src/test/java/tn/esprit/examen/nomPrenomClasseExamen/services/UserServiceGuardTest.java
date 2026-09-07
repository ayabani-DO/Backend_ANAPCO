package tn.esprit.examen.nomPrenomClasseExamen.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.entities.User;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;
import tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests (no Spring context, no DB) for the business safeguards in
 * {@link UserService}: no self-lockout, and always keep at least one ADMIN.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceGuardTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @InjectMocks
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Role role(String name) {
        return Role.builder().name(name).build();
    }

    private static User userWith(Long id, String email, Role... roles) {
        return User.builder()
                .idUser(id)
                .email(email)
                .roles(new HashSet<>(Set.of(roles)))
                .build();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    @Test
    void removingRoleFromLastAdmin_isRejected() {
        User admin = userWith(1L, "admin@anapco.com", role(SecurityRoles.ADMIN));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleName(SecurityRoles.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.removeRoleFromUser(1L, "ADMIN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last ADMIN");
    }

    @Test
    void demotingLastAdminViaReplace_isRejected() {
        User admin = userWith(1L, "admin@anapco.com", role(SecurityRoles.ADMIN));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(roleRepository.findByName("VIEWER")).thenReturn(Optional.of(role(SecurityRoles.VIEWER)));
        when(userRepository.countByRoleName(SecurityRoles.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.assignAndReplaceRoleToUser(1L, "VIEWER"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disablingYourOwnAccount_isRejected() {
        authenticateAs("me@anapco.com");
        User me = userWith(7L, "me@anapco.com", role(SecurityRoles.ADMIN));
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> userService.setAccountEnabled(7L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    void lockingLastAdmin_isRejected() {
        User admin = userWith(1L, "admin@anapco.com", role(SecurityRoles.ADMIN));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleName(SecurityRoles.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.banUser(1L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last ADMIN");
    }

    @Test
    void lockingNonAdminUser_isAllowed() {
        User viewer = userWith(5L, "viewer@anapco.com", role(SecurityRoles.VIEWER));
        when(userRepository.findById(5L)).thenReturn(Optional.of(viewer));
        lenient().when(userRepository.countByRoleName(SecurityRoles.ADMIN)).thenReturn(3L);

        assertDoesNotThrow(() -> userService.banUser(5L, true));
        assertThat(viewer.isAccountLocked()).isTrue();
        verify(userRepository).save(viewer);
    }

    @Test
    void unlockingIsNeverBlocked() {
        User admin = userWith(1L, "admin@anapco.com", role(SecurityRoles.ADMIN));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        // Re-enabling / unlocking must not consult the admin-count guard at all.
        assertDoesNotThrow(() -> userService.banUser(1L, false));
        assertThat(admin.isAccountLocked()).isFalse();
    }
}
