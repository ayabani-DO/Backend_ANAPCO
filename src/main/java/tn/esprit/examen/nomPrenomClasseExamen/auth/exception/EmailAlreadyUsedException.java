package tn.esprit.examen.nomPrenomClasseExamen.auth.exception;

/** Registration attempted with an email that already belongs to an account. Maps to HTTP 409. */
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}
