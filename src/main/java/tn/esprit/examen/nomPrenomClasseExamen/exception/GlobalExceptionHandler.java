package tn.esprit.examen.nomPrenomClasseExamen.exception;

import jakarta.mail.MessagingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.ActivationTokenExpiredException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.EmailAlreadyUsedException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidActivationTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidGoogleTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.InvalidResetTokenException;
import tn.esprit.examen.nomPrenomClasseExamen.auth.exception.ResetTokenExpiredException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application-wide exception handler for clean, consistent JSON error bodies.
 *
 * <p>Scoped to the core + auth packages only: the {@code ai} package already ships its own
 * {@code @RestControllerAdvice} ({@code AssistantExceptionHandler}) and must keep it.
 *
 * <p>Deliberately narrow — it does NOT declare a catch-all {@code Exception} handler, so that
 * Spring Security's {@link AccessDeniedException} (raised by method security) is re-emitted as a
 * clean 403 rather than being swallowed into a 500, and unexpected errors still surface as 500 with
 * no stack trace in the body.
 */
@RestControllerAdvice(basePackages = {
        "tn.esprit.examen.nomPrenomClasseExamen.controllers",
        "tn.esprit.examen.nomPrenomClasseExamen.auth",
        "tn.esprit.examen.nomPrenomClasseExamen.cost",
        "tn.esprit.examen.nomPrenomClasseExamen.rul",
        "tn.esprit.examen.nomPrenomClasseExamen.market",
        "tn.esprit.examen.nomPrenomClasseExamen.weather"
})
public class GlobalExceptionHandler {

    // ── Authorization / authentication ──────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    /**
     * Authentication failures raised from a controller (e.g. {@code POST /auth/authenticate}).
     * URL-level auth failures are still handled by {@code RestAuthenticationEntryPoint}.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        String message;
        if (ex instanceof DisabledException) {
            message = "Account is not activated yet. Please use the activation link sent to your email.";
        } else if (ex instanceof LockedException) {
            message = "Account is locked. Contact an administrator.";
        } else if (ex instanceof BadCredentialsException) {
            message = "Invalid email or password";
        } else {
            message = "Authentication failed";
        }
        return build(HttpStatus.UNAUTHORIZED, message);
    }

    // ── Auth lifecycle ─────────────────────────────────────────────────────

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidActivationTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidActivationToken(InvalidActivationTokenException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ActivationTokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredActivationToken(ActivationTokenExpiredException ex) {
        return build(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidGoogleToken(InvalidGoogleTokenException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidResetToken(InvalidResetTokenException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResetTokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredResetToken(ResetTokenExpiredException ex) {
        return build(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<Map<String, Object>> handleMessaging(MessagingException ex) {
        return build(HttpStatus.BAD_GATEWAY,
                "The email could not be sent. Please check the mail configuration and try again.");
    }

    // ── Request shape ──────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or missing request body");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Business-rule conflicts, e.g. trying to lock/disable your own account, to remove the last
     * ADMIN, or a role that was never seeded. Mapped to 409 Conflict rather than a generic 500.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.name());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
