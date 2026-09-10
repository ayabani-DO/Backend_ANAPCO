package tn.esprit.examen.nomPrenomClasseExamen.entities;

/**
 * Purpose of a {@link Token}. Persisted as a string in {@code token.token_type}.
 *
 * <p>A {@code NULL} column value means a <b>pre-Part-2B legacy token</b> whose purpose was never
 * recorded; such tokens are rejected by every flow ({@code getType() != <expected>} is true for
 * {@code null}) and the user must request a fresh activation / reset link.
 *
 * <p>Runtime authorization must switch on this stored value only — never on the token's format
 * or length.
 */
public enum TokenType {
    ACTIVATION,
    PASSWORD_RESET
}
