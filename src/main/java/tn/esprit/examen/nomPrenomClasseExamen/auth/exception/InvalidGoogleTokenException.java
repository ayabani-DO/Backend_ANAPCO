package tn.esprit.examen.nomPrenomClasseExamen.auth.exception;

/** The supplied Google ID token could not be verified. Maps to HTTP 401. */
public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException(String message) {
        super(message);
    }
}
