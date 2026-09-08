package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

/**
 * Raised when a currency conversion cannot be performed because the required historical FX rate
 * is not available.
 *
 * <p>The FX foundation never substitutes a raw local amount for a converted one, so callers that
 * need a value (rather than a {@link CurrencyConverter.ConversionResult}) must handle this
 * exception explicitly.
 */
public class FxRateUnavailableException extends RuntimeException {

    public FxRateUnavailableException(String message) {
        super(message);
    }

    public FxRateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
