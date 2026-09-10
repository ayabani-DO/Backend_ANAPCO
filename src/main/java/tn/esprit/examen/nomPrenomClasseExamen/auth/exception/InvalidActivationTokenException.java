package tn.esprit.examen.nomPrenomClasseExamen.auth.exception;

/** Activation token does not exist or has already been used. Maps to HTTP 400. */
public class InvalidActivationTokenException extends RuntimeException {
    public InvalidActivationTokenException(String message) {
        super(message);
    }
}
