package tn.esprit.examen.nomPrenomClasseExamen.auth.exception;

/** Password-reset token does not exist (or is otherwise unusable). Maps to HTTP 400. */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException(String message) {
        super(message);
    }
}
