package tn.esprit.examen.nomPrenomClasseExamen.analytics.services;

import tn.esprit.examen.nomPrenomClasseExamen.analytics.dto.FxGap;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for monetary components that must be normalised to the reporting currency.
 *
 * <p>Every component is added as a {@link CurrencyConverter.ConversionResult}: converted components
 * are summed, un-converted ones mark the accumulator incomplete and are recorded as {@link FxGap}s.
 * This keeps the "partial total + explicit disclosure" policy in one place.
 */
public final class ReportingAmount {

    private double total;
    private boolean complete = true;
    private final List<FxGap> gaps = new ArrayList<>();

    public ReportingAmount add(CurrencyConverter.ConversionResult result, int year, int month) {
        if (result.converted()) {
            total += result.convertedAmount();
        } else {
            complete = false;
            gaps.add(new FxGap(result.sourceCurrency(), period(year, month),
                    result.originalAmount(), result.reason()));
        }
        return this;
    }

    /** Fold another accumulator's result into this one. */
    public ReportingAmount merge(ReportingAmount other) {
        this.total += other.total;
        this.complete = this.complete && other.complete;
        this.gaps.addAll(other.gaps);
        return this;
    }

    public double total() {
        return Math.round(total * 100.0) / 100.0;
    }

    public boolean complete() {
        return complete;
    }

    public List<FxGap> gaps() {
        return List.copyOf(gaps);
    }

    private static String period(int year, int month) {
        return year + "-" + (month < 10 ? "0" + month : Integer.toString(month));
    }
}
