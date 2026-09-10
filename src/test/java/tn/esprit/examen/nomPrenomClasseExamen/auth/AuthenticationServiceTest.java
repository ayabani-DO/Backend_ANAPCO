package tn.esprit.examen.nomPrenomClasseExamen.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.ActivationTokenExpiredException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidActivationTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidGoogleTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidResetTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.ResetTokenExpiredException;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Role;
import tn.esprit.examen.nomPrenomClasseExamen.entities.Token;
import tn.esprit.examen.nomPrenomClasseExamen.entities.TokenType;
import tn.esprit.examen.nomPrenomClasseExamen.entities.User;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.RoleRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.TokenRepository;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;
import tn.esprit.examen.nomPrenomClasseExamen.security.JwtService;
import tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles;
import tn.esprit.examen.nomPrenomClasseExamen.services.EmailService;
import tn.esprit.examen.nomPrenomClasseExamen.services.EmailTemplateName;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for the Part 2A auth-service hardening: forgot-password enumeration,
 * update-password ownership, reset-password typed exceptions + length, and Google-login checks.
 * No Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRepository userRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(roleRepository, passwordEncoder, userRepository,
                tokenRepository, emailService, authenticationManager, jwtService);
        ReflectionTestUtils.setField(service, "resetPasswordUrl", "http://localhost:4200/reset-password");
        ReflectionTestUtils.setField(service, "activationUrl", "http://localhost:8089/auth/activate-account");
        ReflectionTestUtils.setField(service, "googleClientId", "test-client-id");
    }

    private static User enabledUser(String email, String passwordHash) {
        return User.builder().idUser(1L).email(email).password(passwordHash)
                .enabled(true).accountLocked(false).build();
    }

    // ---------- forgot-password: no account enumeration ----------

    @Test
    void forgotPassword_unknownEmail_isNoOp() throws Exception {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        service.forgotPassword("nobody@example.com");

        verifyNoInteractions(tokenRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void forgotPassword_knownEmail_createsTokenAndSendsEmail() throws Exception {
        when(userRepository.findByEmailIgnoreCase("john@example.com"))
                .thenReturn(Optional.of(enabledUser("john@example.com", "ENC")));

        service.forgotPassword("john@example.com");

        verify(tokenRepository).save(any(Token.class));
        verify(emailService).SendEmail(eq("john@example.com"), any(), eq(EmailTemplateName.RESET_PASSWORD),
                contains("?token="), any(), any());
    }

    // ---------- update-password: ownership from the authenticated principal ----------

    private static ResetPasswordDto dto(String email, String current, String next) {
        ResetPasswordDto d = new ResetPasswordDto();
        d.setEmail(email);
        d.setCurrentPassword(current);
        d.setNewPassword(next);
        return d;
    }

    @Test
    void updatePassword_updatesOwnAccount() {
        User me = enabledUser("me@example.com", "ENC_OLD");
        when(userRepository.findByEmailIgnoreCase("me@example.com")).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("oldpassword", "ENC_OLD")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("ENC_NEW");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.updatePassword("me@example.com", dto("me@example.com", "oldpassword", "newpassword"));

        assertThat(me.getPassword()).isEqualTo("ENC_NEW");
        verify(userRepository).save(me);
    }

    @Test
    void updatePassword_wrongCurrentPassword_isRejected() {
        User me = enabledUser("me@example.com", "ENC_OLD");
        when(userRepository.findByEmailIgnoreCase("me@example.com")).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("wrong", "ENC_OLD")).thenReturn(false);

        assertThatThrownBy(() -> service.updatePassword("me@example.com",
                dto("me@example.com", "wrong", "newpassword")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updatePassword_bodyEmailDiffersFromPrincipal_isRejected() {
        User me = enabledUser("me@example.com", "ENC_OLD");
        when(userRepository.findByEmailIgnoreCase("me@example.com")).thenReturn(Optional.of(me));

        // Authenticated as me@example.com, but the body tries to target victim@example.com.
        assertThatThrownBy(() -> service.updatePassword("me@example.com",
                dto("victim@example.com", "oldpassword", "newpassword")))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    // ---------- reset-password: typed exceptions + length ----------

    @Test
    void resetPassword_shortNewPassword_isRejected() {
        assertThatThrownBy(() -> service.resetPassword("any-token", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void resetPassword_unknownToken_throwsInvalidResetToken() {
        when(tokenRepository.findByToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("nope", "newpassword"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void resetPassword_expiredToken_throwsResetTokenExpired() {
        Token expired = Token.builder().token("old").type(TokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .user(enabledUser("john@example.com", "ENC")).build();
        when(tokenRepository.findByToken("old")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resetPassword("old", "newpassword"))
                .isInstanceOf(ResetTokenExpiredException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndConsumesToken() {
        User user = enabledUser("john@example.com", "ENC_OLD");
        Token valid = Token.builder().token("good").type(TokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).user(user).build();
        when(tokenRepository.findByToken("good")).thenReturn(Optional.of(valid));
        when(passwordEncoder.encode("newpassword")).thenReturn("ENC_NEW");

        service.resetPassword("good", "newpassword");

        assertThat(user.getPassword()).isEqualTo("ENC_NEW");
        verify(userRepository).save(user);
        verify(tokenRepository).delete(valid);
    }

    // ---------- Part 2B: token purpose separation ----------

    private static Token tokenOf(TokenType type, String value) {
        return Token.builder().token(value).type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .user(enabledUser("john@example.com", "ENC_OLD")).build();
    }

    @Test
    void resetPassword_withActivationTypeToken_isRejected() {
        when(tokenRepository.findByToken("123456"))
                .thenReturn(Optional.of(tokenOf(TokenType.ACTIVATION, "123456")));

        assertThatThrownBy(() -> service.resetPassword("123456", "newpassword"))
                .isInstanceOf(InvalidResetTokenException.class)
                .hasMessageContaining("not a password-reset link");
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    void resetPassword_withLegacyNullTypeToken_isRejected() {
        Token legacy = Token.builder().token("legacy-uuid").type(null)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .user(enabledUser("john@example.com", "ENC")).build();
        when(tokenRepository.findByToken("legacy-uuid")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.resetPassword("legacy-uuid", "newpassword"))
                .isInstanceOf(InvalidResetTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_consumedTokenCannotBeReused() {
        User user = enabledUser("john@example.com", "ENC_OLD");
        Token valid = Token.builder().token("once").type(TokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).user(user).build();
        // First lookup finds it; after resetPassword deletes it, the second lookup finds nothing.
        when(tokenRepository.findByToken("once"))
                .thenReturn(Optional.of(valid))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("newpassword")).thenReturn("ENC_NEW");

        service.resetPassword("once", "newpassword");
        verify(tokenRepository).delete(valid);

        assertThatThrownBy(() -> service.resetPassword("once", "another8chars"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void activateAccount_withResetTypeToken_isRejected() {
        when(tokenRepository.findByToken("reset-uuid"))
                .thenReturn(Optional.of(tokenOf(TokenType.PASSWORD_RESET, "reset-uuid")));

        assertThatThrownBy(() -> service.activateaccount("reset-uuid"))
                .isInstanceOf(InvalidActivationTokenException.class)
                .hasMessageContaining("not an account-activation link");
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateAccount_withLegacyNullTypeToken_isRejected() {
        Token legacy = Token.builder().token("legacy-6digit").type(null)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .user(enabledUser("john@example.com", "ENC")).build();
        when(tokenRepository.findByToken("legacy-6digit")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.activateaccount("legacy-6digit"))
                .isInstanceOf(InvalidActivationTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void activateAccount_withCorrectActivationToken_succeeds() {
        User user = User.builder().idUser(7L).email("new@example.com").password("ENC")
                .enabled(false).accountLocked(true).build();
        Token activation = Token.builder().token("654321").type(TokenType.ACTIVATION)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).user(user).build();
        when(tokenRepository.findByToken("654321")).thenReturn(Optional.of(activation));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.activateaccount("654321");

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isAccountLocked()).isFalse();
        assertThat(activation.getValidatedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void forgotPassword_invalidatesPreviousUnusedResetTokensOnly() throws Exception {
        when(userRepository.findByEmailIgnoreCase("john@example.com"))
                .thenReturn(Optional.of(enabledUser("john@example.com", "ENC")));

        service.forgotPassword("john@example.com");

        verify(tokenRepository).deletePreviousUnusedTokens(1L, TokenType.PASSWORD_RESET);
        verify(tokenRepository, never()).deletePreviousUnusedTokens(any(), eq(TokenType.ACTIVATION));
    }

    @Test
    void forgotPassword_newTokenStoresPasswordResetType() throws Exception {
        when(userRepository.findByEmailIgnoreCase("john@example.com"))
                .thenReturn(Optional.of(enabledUser("john@example.com", "ENC")));
        ArgumentCaptor<Token> saved = ArgumentCaptor.forClass(Token.class);

        service.forgotPassword("john@example.com");

        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(TokenType.PASSWORD_RESET);
    }

    @Test
    void register_activationTokenStoresActivationType() throws Exception {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("VIEWER"))
                .thenReturn(Optional.of(Role.builder().name("VIEWER").build()));
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setIdUser(42L);
            return u;
        });
        ArgumentCaptor<Token> saved = ArgumentCaptor.forClass(Token.class);

        RegistrationRequest req = RegistrationRequest.builder()
                .firstName("New").lastName("User").email("new@example.com").password("password1")
                .build();
        service.register(req, java.util.List.of("VIEWER"));

        verify(tokenRepository).deletePreviousUnusedTokens(42L, TokenType.ACTIVATION);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(TokenType.ACTIVATION);
    }

    // ---------- Google login hardening ----------

    private static GoogleIdToken.Payload googlePayload(String email, Boolean emailVerified) {
        GoogleIdToken.Payload p = new GoogleIdToken.Payload();
        p.setEmail(email);
        p.setEmailVerified(emailVerified);
        p.set("given_name", "Grace");
        p.set("family_name", "Hopper");
        return p;
    }

    @Test
    void google_invalidToken_throwsInvalidGoogleToken() {
        // A syntactically invalid ID token never verifies.
        assertThatThrownBy(() -> service.authenticateWithGoogle("not-a-real-id-token"))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void google_unverifiedEmail_isRejected() {
        assertThatThrownBy(() ->
                service.authenticateVerifiedGoogleUser(googlePayload("grace@example.com", false)))
                .isInstanceOf(InvalidGoogleTokenException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void google_missingEmailVerifiedClaim_isRejected() {
        assertThatThrownBy(() ->
                service.authenticateVerifiedGoogleUser(googlePayload("grace@example.com", null)))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void google_firstTimeVerifiedUser_getsViewerEnabledUnlocked() {
        when(userRepository.findByEmailIgnoreCase("grace@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(SecurityRoles.VIEWER))
                .thenReturn(Optional.of(Role.builder().name(SecurityRoles.VIEWER).build()));
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        AuthenficationResponse resp =
                service.authenticateVerifiedGoogleUser(googlePayload("grace@example.com", true));

        assertThat(resp.getToken()).isEqualTo("jwt-token");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().isAccountLocked()).isFalse();
        assertThat(saved.getValue().getRoles()).extracting(Role::getName)
                .containsExactly(SecurityRoles.VIEWER);
    }

    @Test
    void google_firstTimeUser_whenViewerRoleMissing_failsClearly_noLegacyUserFallback() {
        when(userRepository.findByEmailIgnoreCase("grace@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(SecurityRoles.VIEWER)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.authenticateVerifiedGoogleUser(googlePayload("grace@example.com", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SecurityRoles.VIEWER);

        // No legacy "USER" fallback, and no user is created when the default role is unavailable.
        verify(roleRepository, never()).findByName("USER");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void google_existingValidUser_isNotDuplicated() {
        User existing = enabledUser("grace@example.com", "ENC");
        existing.setRoles(new HashSet<>(Set.of(Role.builder().name(SecurityRoles.VIEWER).build())));
        when(userRepository.findByEmailIgnoreCase("grace@example.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token");

        AuthenficationResponse resp =
                service.authenticateVerifiedGoogleUser(googlePayload("grace@example.com", true));

        assertThat(resp.getToken()).isEqualTo("jwt-token");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void google_existingDisabledUser_isRejected() {
        User disabled = User.builder().idUser(2L).email("grace@example.com").password("ENC")
                .enabled(false).accountLocked(false).build();
        when(userRepository.findByEmailIgnoreCase("grace@example.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() ->
                service.authenticateVerifiedGoogleUser(googlePayload("grace@example.com", true)))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void google_existingLockedUser_isRejected() {
        User locked = User.builder().idUser(3L).email("grace@example.com").password("ENC")
                .enabled(true).accountLocked(true).build();
        when(userRepository.findByEmailIgnoreCase("grace@example.com")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() ->
                service.authenticateVerifiedGoogleUser(googlePayload("grace@example.com", true)))
                .isInstanceOf(LockedException.class);
    }

    // ---------- User.roles @Builder.Default ----------

    @Test
    void userBuilder_withoutRoles_yieldsNonNullEmptySet() {
        User u = User.builder().email("x@example.com").build();
        assertThat(u.getRoles()).isNotNull().isEmpty();
    }
}
