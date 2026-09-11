package tn.esprit.examen.nomPrenomClasseExamen.exception;

/**
 * Thrown when an operation (typically a delete) is refused because other records still
 * depend on the target entity. Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 *
 * <p>The operational and financial history is never cascade-deleted; the caller must remove or
 * reassign the dependent records first.
 */
public class DependencyExistsException extends RuntimeException {

    public DependencyExistsException(String message) {
        super(message);
    }
}
