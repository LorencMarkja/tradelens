package io.github.lorencmarkja.tradelens.economics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Margin formulas of the comparison. Intermediate values keep DECIMAL128 precision;
 * rounding happens only when values are presented (see {@link Money}).
 */
public final class MarginCalculator {

    public static final MathContext MC = MathContext.DECIMAL128;

    private MarginCalculator() {
    }

    /** Financed capital per mt x annual rate x financing days / day-count basis (simple interest). */
    public static BigDecimal financingCostPerMt(LotTerms lot, int financingDays) {
        return lot.financedCapitalPerMt()
                .multiply(lot.annualRate(), MC)
                .multiply(BigDecimal.valueOf(financingDays), MC)
                .divide(BigDecimal.valueOf(lot.dayCountBasis()), MC);
    }

    public static OfferEconomics compute(LotTerms lot, OfferSnapshot offer) {
        List<String> missing = new ArrayList<>();
        if (offer.salePricePerMt() == null) {
            missing.add("Sale price");
        }
        if (offer.logisticsCostPerMt() == null) {
            missing.add("Logistics cost");
        }
        if (offer.financingDays() == null) {
            missing.add("Financing days");
        }
        BigDecimal financing = offer.financingDays() == null ? null : financingCostPerMt(lot, offer.financingDays());
        if (!missing.isEmpty()) {
            return new OfferEconomics(List.copyOf(missing), financing, null, null);
        }
        BigDecimal margin = offer.salePricePerMt()
                .subtract(lot.purchaseCostPerMt(), MC)
                .subtract(offer.logisticsCostPerMt(), MC)
                .subtract(financing, MC);
        BigDecimal total = margin.multiply(lot.quantityMt(), MC);
        return new OfferEconomics(List.of(), financing, margin, total);
    }

    /**
     * Maximum logistics cost per mt at which the offer matches the competing margin,
     * other inputs unchanged: sale - purchase - financing - competing margin.
     */
    public static BigDecimal logisticsThreshold(LotTerms lot, BigDecimal salePricePerMt, BigDecimal financingCostPerMt,
                                                BigDecimal competingMarginPerMt) {
        return salePricePerMt
                .subtract(lot.purchaseCostPerMt(), MC)
                .subtract(financingCostPerMt, MC)
                .subtract(competingMarginPerMt, MC);
    }
}
