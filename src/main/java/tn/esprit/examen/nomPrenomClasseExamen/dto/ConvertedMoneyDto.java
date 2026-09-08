package tn.esprit.examen.nomPrenomClasseExamen.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result of a <b>record-scoped, display-only</b> currency conversion of one stored monetary record
 * (a BudgetMonthly / ManualExpense amount, or an Incident / Maintenance cost).
 *
 * <p>The backend derives {@code originalAmount}, {@code originalCurrency} and the business period
 * from the record itself — the caller (Angular) supplies only the record id and the target currency.
 * Nothing here is persisted.
 *
 * <p>On FX failure {@code convertedAmount} is {@code null}, {@code rateAvailable} is {@code false}
 * and {@code unavailableReason} explains why. {@code originalAmount} is <b>never</b> copied into
 * {@code convertedAmount}.
 */
@Data
@Builder
public class ConvertedMoneyDto {

    /** BUDGET_MONTHLY | MANUAL_EXPENSE | INCIDENT | MAINTENANCE */
    private String recordType;
    private Long recordId;
    /** For INCIDENT: "costReal" or "costEstimated". Null for the other record types. */
    private String sourceField;

    private Double originalAmount;
    private String originalCurrency;

    private String targetCurrency;
    /** Converted value, or {@code null} when the conversion is unavailable. */
    private Double convertedAmount;

    private Integer businessYear;
    private Integer businessMonth;
    /** "yyyy-MM" — the record's own business month, used for the FX lookup. */
    private String period;

    private boolean rateAvailable;
    /** Effective end-to-end multiplier actually applied ({@code convertedAmount / originalAmount}); null when unavailable or amount is 0. */
    private Double rateUsed;
    /** Period whose rate was used — always equals {@link #period} (exact-month lookup, no fallback). */
    private String ratePeriodUsed;

    /** Populated only when {@code rateAvailable == false}. */
    private String unavailableReason;
}
