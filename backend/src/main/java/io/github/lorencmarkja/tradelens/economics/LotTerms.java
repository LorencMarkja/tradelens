package io.github.lorencmarkja.tradelens.economics;

import java.math.BigDecimal;

/** Lot-level terms used by every margin calculation. All amounts are USD per metric tonne. */
public record LotTerms(
        BigDecimal quantityMt,
        BigDecimal purchaseCostPerMt,
        BigDecimal financedCapitalPerMt,
        BigDecimal annualRate,
        int dayCountBasis) {
}
