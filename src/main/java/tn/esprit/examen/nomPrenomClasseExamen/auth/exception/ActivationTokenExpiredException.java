package tn.esprit.examen.nomPrenomClasseExamen.auth.exception;

/** Activation token was valid but has expired. Maps to HTTP 410 (Gone). */
public class ActivationTokenExpiredException extends RuntimeException {
    public ActivationTokenExpiredException(String message) {
        super(message);
    }
}
