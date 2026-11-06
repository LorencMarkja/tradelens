package io.github.lorencmarkja.tradelens.economics;

import java.math.BigDecimal;
import java.util.List;

public record EvaluatedOffer(
        OfferSnapshot offer,
        OfferEconomics economics,
        OfferStatus status,
        List<String> statusReasons,
        boolean highest,
        Integer rank,
        Threshold threshold) {

    /** Logistics cost per mt at which this offer matches the best competing ranked offer. */
    public record Threshold(long competitorOfferId, String competitorBuyer, BigDecimal competitorMarginPerMt,
                            BigDecimal logisticsCostPerMt) {
    }
}
