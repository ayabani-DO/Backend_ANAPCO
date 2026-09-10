package tn.esprit.examen.nomPrenomClasseExamen.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;
import tn.esprit.examen.nomPrenomClasseExamen.security.SecurityRoles;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody @Valid RegistrationRequest request)
            throws MessagingException {
        List<String> roles = List.of(SecurityRoles.VIEWER);
        authService.register(request, roles);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "PENDING_ACTIVATION",
                "message", "Registration accepted. Check your email for the activation link."));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthenficationResponse> authenticateWithGoogle(
            @RequestParam String googleToken
    ) throws IOException, GeneralSecurityException {
        return ResponseEntity.ok(authService.authenticateWithGoogle(googleToken));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenficationResponse> authenticate(
            @RequestBody @Valid AuthenficationRequest request
    ) {
        return ResponseEntity.ok(authService.authenficate(request));
    }

    @GetMapping("/activate-account")
    public ResponseEntity<Map<String, String>> confirm(@RequestParam String token) {
        authService.activateaccount(token);
        return ResponseEntity.ok(Map.of(
                "status", "ACTIVATED",
                "message", "Account activated. You can now log in."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestParam String email)
            throws MessagingException {
        authService.forgotPassword(email);
        // Identical response whether or not the email is registered — no account enumeration.
        return ResponseEntity.ok(Map.of(
                "message", "If an account exists for this email, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword
    ) {
        authService.resetPassword(token, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully"));
    }

    @PostMapping("/update-password")
    public ResponseEntity<Map<String, String>> updatePassword(
            Authentication authentication,
            @RequestBody @Valid ResetPasswordDto resetPasswordDto) {
        // Target account is the JWT principal, never resetPasswordDto.getEmail().
        authService.updatePassword(authentication.getName(), resetPasswordDto);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
