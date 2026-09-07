package tn.esprit.examen.nomPrenomClasseExamen.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Application-wide exception handler for clean, consistent JSON error bodies.
 *
 * <p>Scoped to the core packages only: the {@code ai} package already ships its own
 * {@code @RestControllerAdvice} ({@code AssistantExceptionHandler}) and must keep it.
 *
 * <p>Deliberately narrow — it does NOT declare a catch-all {@code Exception} handler,
 * so that Spring Security's {@link AccessDeniedException} (raised by method security)
 * is re-emitted as a clean 403 rather than being swallowed into a 500.
 */
@RestControllerAdvice(basePackages = {
        "tn.esprit.examen.nomPrenomClasseExamen.controllers",
        "tn.esprit.examen.nomPrenomClasseExamen.cost",
        "tn.esprit.examen.nomPrenomClasseExamen.rul",
        "tn.esprit.examen.nomPrenomClasseExamen.market",
        "tn.esprit.examen.nomPrenomClasseExamen.weather"
})
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Business-rule conflicts, e.g. trying to lock/disable your own account or to
     * remove the last ADMIN. Mapped to 409 Conflict rather than a generic 500.
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
