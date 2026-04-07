package tn.esprit.examen.nomPrenomClasseExamen.auth;


import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.examen.nomPrenomClasseExamen.entities.User;
import tn.esprit.examen.nomPrenomClasseExamen.repositories.UserRepository;

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


    @PostMapping("/Register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<?> register(@RequestBody @Valid RegistrationRequest request) throws MessagingException {
        List<String> roles = List.of("USER");
        authService.register(request, roles);
        return ResponseEntity.accepted().build();

    }
    @PostMapping("/google")
    public ResponseEntity<AuthenficationResponse> authenticateWithGoogle(
            @RequestParam String  googleToken
    ) throws IOException, GeneralSecurityException {
        return ResponseEntity.ok(authService.authenticateWithGoogle(googleToken));
    }


    @PostMapping("/authenticate")
    private ResponseEntity<AuthenficationResponse> authenticate(
            @RequestBody @Valid AuthenficationRequest request
    ){
        return ResponseEntity.ok(authService.authenficate(request));
    }
    @GetMapping("activate-account")
    public void confirm(
            @RequestParam String token
    ) throws MessagingException {
        authService.activateaccount(token);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) throws MessagingException {
        authService.forgotPassword(email);
        return ResponseEntity.ok("Password reset link sent to email");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword
    ) {
        try {
            authService.resetPassword(token, newPassword);
            return ResponseEntity.ok().body(
                    Map.of("message", "Password has been reset successfully")
            );
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/update-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDto resetPasswordDto) {
        try {
            User updatedUser = authService.updatePassword(resetPasswordDto);
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

}