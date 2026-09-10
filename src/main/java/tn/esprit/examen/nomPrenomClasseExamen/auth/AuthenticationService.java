package tn.esprit.examen.nomPrenomClasseExamen.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.ActivationTokenExpiredException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.EmailAlreadyUsedException;
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final RoleRepository role;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    @Value("${application.mailing.activation-url}")
    private String activationUrl;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;
    @Value("${application.mailing.reset-password-url}")
    private String resetPasswordUrl;

    public AuthenficationResponse authenticateWithGoogle(String googleToken) throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory()
        )
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(googleToken);
        } catch (IllegalArgumentException ex) {
            throw new InvalidGoogleTokenException("Invalid Google token");
        }
        if (idToken == null) {
            // Covers a bad signature, wrong audience (not our GOOGLE_CLIENT_ID), or an expired token.
            throw new InvalidGoogleTokenException("Invalid Google token");
        }

        return authenticateVerifiedGoogleUser(idToken.getPayload());
    }

    /**
     * Turns an <b>already cryptographically-verified</b> Google ID-token payload into an ANAPCO JWT.
     *
     * <p>Package-private so it can be unit-tested without a live Google verifier. Callers other than
     * {@link #authenticateWithGoogle(String)} must guarantee the payload came from a successful
     * {@code GoogleIdTokenVerifier.verify(...)} (audience = our client id, valid signature, not expired).
     *
     * <p>Trust boundary: only {@code email}, {@code email_verified} and the display-name claims are
     * read. Roles are <b>never</b> taken from Google — a first-time user is always created as
     * {@code VIEWER}, enabled and unlocked. An existing account that is disabled or locked cannot
     * obtain a usable login here, exactly as with password authentication.
     */
    AuthenficationResponse authenticateVerifiedGoogleUser(GoogleIdToken.Payload payload) {
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidGoogleTokenException("Google account email is not verified");
        }

        String email = payload.getEmail();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");

        LocalDate defaultDateOfBirth = LocalDate.of(2000, 1, 1);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    Role userRole = resolveDefaultRole();

                    User newUser = new User();
                    newUser.setFirstName(firstName);
                    newUser.setLastName(lastName);
                    newUser.setEmail(email);
                    newUser.setDateOfBirth(defaultDateOfBirth);
                    newUser.setPassword(passwordEncoder.encode(generateRandomPassword()));
                    newUser.setAccountLocked(false);
                    newUser.setEnabled(true);

                    Set<Role> roles = new HashSet<>();
                    roles.add(userRole);
                    newUser.setRoles(roles);

                    return userRepository.save(newUser);
                });

        if (!user.isEnabled()) {
            throw new DisabledException(
                    "Account is not activated yet. Please use the activation link sent to your email.");
        }
        if (user.isAccountLocked()) {
            throw new LockedException("Account is locked. Contact an administrator.");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("fullName", user.FullName());
        claims.put("email", user.getEmail());
        claims.put("dateOfBirth", user.getDateOfBirth());

        String jwtToken = jwtService.generateToken(claims, user);
        return AuthenficationResponse.builder().token(jwtToken).build();
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Registers a disabled user and sends the activation email <b>synchronously</b>. If the email
     * cannot be sent the whole operation is rolled back (no orphan user / token) and the caller
     * receives a clear failure — never a silent "accepted".
     */
    @Transactional
    public void register(RegistrationRequest request, List<String> roleNames) throws MessagingException {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new EmailAlreadyUsedException("Email already in use");
        }

        List<Role> roles = roleNames.stream()
                .map(roleName -> role.findByName(roleName.toUpperCase())
                        .orElseThrow(() -> new IllegalStateException("Role " + roleName + " was not initialized")))
                .collect(Collectors.toList());

        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .accountLocked(false)
                .enabled(false)
                .roles(new HashSet<>(roles))
                .build();
        userRepository.save(user);
        sendValidationEmail(user);
    }

    private void sendValidationEmail(User user) throws MessagingException {
        var newToken = generateAndSaveActivationToken(user);
        emailService.SendEmail(
                user.getEmail(),
                user.FullName(),
                EmailTemplateName.ACTIVATE_ACCOUNT,
                buildActivationLink(newToken),
                newToken,
                "account activation"
        );
    }

    /** {activationUrl}?token={code} — the emailed link that hits {@code GET /auth/activate-account}. */
    private String buildActivationLink(String token) {
        String separator = activationUrl.contains("?") ? "&" : "?";
        return activationUrl + separator + "token=" + token;
    }

    private String generateAndSaveActivationToken(User user) {
        // Token hygiene: a fresh activation token supersedes any earlier unused ACTIVATION token
        // for this user. Never touches PASSWORD_RESET tokens.
        tokenRepository.deletePreviousUnusedTokens(user.getIdUser(), TokenType.ACTIVATION);

        String generatedToken = generateActivationCode(6);
        var token = Token.builder()
                .token(generatedToken)
                .type(TokenType.ACTIVATION)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .user(user)
                .build();
        tokenRepository.save(token);
        return generatedToken;
    }

    private String generateActivationCode(int length) {
        String characters = "0123456789";
        StringBuilder codebuilder = new StringBuilder();
        SecureRandom secureRandom = new SecureRandom();
        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(characters.length());
            codebuilder.append(characters.charAt(randomIndex));
        }
        return codebuilder.toString();
    }

    public AuthenficationResponse authenficate(AuthenficationRequest request) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()
                )
        );
        var claims = new HashMap<String, Object>();
        var user = ((User) auth.getPrincipal());
        claims.put("fullName", user.FullName());
        claims.put("dateOfBirth", user.getDateOfBirth());
        var jwtToken = jwtService.generateToken(claims, user);
        return AuthenficationResponse.builder().token(jwtToken).build();
    }

    @Transactional
    public void activateaccount(String token) {
        Token savedToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidActivationTokenException("Invalid activation token"));

        // Purpose separation (Part 2B): only an ACTIVATION token activates an account. A password-reset
        // token, or a pre-2B legacy token with a null type, is rejected here.
        if (savedToken.getType() != TokenType.ACTIVATION) {
            throw new InvalidActivationTokenException("This link is not an account-activation link");
        }

        if (savedToken.getValidatedAt() != null) {
            throw new InvalidActivationTokenException("This activation token has already been used");
        }

        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
            throw new ActivationTokenExpiredException(
                    "Activation token has expired. Please register again to receive a new activation email.");
        }

        User user = userRepository.findById(savedToken.getUser().getIdUser())
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found"));
        user.setEnabled(true);
        user.setAccountLocked(false);
        userRepository.save(user);

        savedToken.setValidatedAt(LocalDateTime.now());
        tokenRepository.save(savedToken);
    }

    /**
     * Starts the password-reset flow. <b>Never reveals whether the email is registered</b>: the
     * controller always returns the same generic 200 body. For an unknown email this method is a
     * no-op (no token row, no email). For a known email it creates a 30-minute reset token and sends
     * the reset email synchronously; if the email cannot be sent the token row is rolled back
     * ({@code rollbackFor = MessagingException.class}) so no orphan token is left behind.
     */
    @Transactional(rollbackFor = MessagingException.class)
    public void forgotPassword(String email) throws MessagingException {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(email);
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();

        // Token hygiene: a fresh reset token supersedes any earlier unused PASSWORD_RESET token for
        // this user. Never touches ACTIVATION tokens. Rolls back with the token insert if the email
        // send fails (method is @Transactional(rollbackFor = MessagingException.class)).
        tokenRepository.deletePreviousUnusedTokens(user.getIdUser(), TokenType.PASSWORD_RESET);

        String resetToken = UUID.randomUUID().toString();
        Token token = Token.builder()
                .token(resetToken)
                .type(TokenType.PASSWORD_RESET)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .user(user)
                .build();

        tokenRepository.save(token);

        String resetLink = resetPasswordUrl + "?token=" + resetToken;

        emailService.SendEmail(
                user.getEmail(),
                user.FullName(),
                EmailTemplateName.RESET_PASSWORD,
                resetLink,
                resetToken,
                "Password Reset Request"
        );
    }

    /**
     * Completes the forgot-password flow with a reset token from the emailed link.
     *
     * <p>Part 2A hardening: enforces the same 8-char minimum as registration, and reports failures
     * with typed exceptions ({@link InvalidResetTokenException} → 400, {@link ResetTokenExpiredException}
     * → 410) handled by the global exception handler instead of a controller catch-all.
     *
     * <p>Part 2B: only a {@link TokenType#PASSWORD_RESET} token is accepted. An activation code, or a
     * pre-2B legacy token with a null type, is rejected with {@link InvalidResetTokenException}. The
     * token is deleted on success, so a consumed token cannot be reused.
     */
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("newPassword: password should be 8 characters long minimum");
        }

        Token savedToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidResetTokenException("Invalid or unknown reset token"));

        if (savedToken.getType() != TokenType.PASSWORD_RESET) {
            throw new InvalidResetTokenException("This link is not a password-reset link");
        }

        if (LocalDateTime.now().isAfter(savedToken.getExpiresAt())) {
            throw new ResetTokenExpiredException(
                    "Reset token has expired. Please request a new password-reset email.");
        }

        User user = savedToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.delete(savedToken);
    }

    /**
     * Changes the password of the <b>currently authenticated</b> user.
     *
     * <p>Part 2A ownership fix: the target account is taken from {@code authenticatedEmail}
     * (the JWT principal), never from {@code dto.getEmail()}. If the body carries a different email
     * the request is rejected with {@link AccessDeniedException} (403) so user A can never target
     * user B by editing the field. The current password is verified against the authenticated user;
     * the 8-char minimum on {@code newPassword} is enforced by bean validation on the DTO.
     */
    public User updatePassword(String authenticatedEmail, ResetPasswordDto resetPasswordDto) {
        User user = userRepository.findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user not found"));

        if (resetPasswordDto.getEmail() != null
                && !resetPasswordDto.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new AccessDeniedException("You can only change your own password");
        }

        if (!passwordEncoder.matches(resetPasswordDto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(resetPasswordDto.getNewPassword()));
        return userRepository.save(user);
    }

    /**
     * The default role for every self-service account (public registration and first-time Google
     * sign-in) is always {@link SecurityRoles#VIEWER}. There is no legacy fallback: if the VIEWER
     * row is somehow not seeded, fail loudly rather than inventing another role.
     */
    private Role resolveDefaultRole() {
        return role.findByName(SecurityRoles.VIEWER)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role " + SecurityRoles.VIEWER + " is not seeded"));
    }
}
