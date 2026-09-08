package tn.esprit.examen.nomPrenomClasseExamen.analytics.dto;

/**
 * One monetary line that could not be normalised to the reporting currency.
 *
 * <p>Exposed on analytics DTOs via {@code fxUnavailable} so the frontend can show a partial total
 * with an explicit disclosure ("excludes N GBP items — rate unavailable") instead of a silently
 * wrong figure.
 */
public record FxGap(String sourceCurrency, String period, double originalAmount, String reason) {
}
