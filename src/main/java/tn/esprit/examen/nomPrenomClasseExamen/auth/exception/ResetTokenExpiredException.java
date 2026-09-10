package tn.esprit.examen.nomPrenomClasseExamen.auth.exception;

/** Password-reset token was valid but has expired. Maps to HTTP 410 (Gone). */
public class ResetTokenExpiredException extends RuntimeException {
    public ResetTokenExpiredException(String message) {
        super(message);
    }
}
