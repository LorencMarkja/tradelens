package io.github.lorencmarkja.tradelens.economics;

import java.math.BigDecimal;
import java.util.List;

/** Unrounded economic result for one offer. Margin values are null when inputs are missing. */
public record OfferEconomics(
        List<String> missingInputs,
        BigDecimal financingCostPerMt,
        BigDecimal marginPerMt,
        BigDecimal totalMargin) {

    public boolean complete() {
        return missingInputs.isEmpty();
    }
}
