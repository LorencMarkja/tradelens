package io.github.lorencmarkja.tradelens.economics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable view of an offer used for evaluation; null economic inputs mean "missing", never zero. */
public record OfferSnapshot(
        long offerId,
        String buyer,
        BigDecimal salePricePerMt,
        BigDecimal logisticsCostPerMt,
        Integer financingDays,
        LocalDate validUntil,
        LocalDate logisticsQuoteExpiry) {

    public OfferSnapshot withSalePrice(BigDecimal value) {
        return new OfferSnapshot(offerId, buyer, value, logisticsCostPerMt, financingDays, validUntil, logisticsQuoteExpiry);
    }

    public OfferSnapshot withLogistics(BigDecimal value) {
        return new OfferSnapshot(offerId, buyer, salePricePerMt, value, financingDays, validUntil, logisticsQuoteExpiry);
    }

    public OfferSnapshot withFinancingDays(Integer value) {
        return new OfferSnapshot(offerId, buyer, salePricePerMt, logisticsCostPerMt, value, validUntil, logisticsQuoteExpiry);
    }

    public OfferSnapshot withValidUntil(LocalDate value) {
        return new OfferSnapshot(offerId, buyer, salePricePerMt, logisticsCostPerMt, financingDays, value, logisticsQuoteExpiry);
    }

    public OfferSnapshot withLogisticsQuoteExpiry(LocalDate value) {
        return new OfferSnapshot(offerId, buyer, salePricePerMt, logisticsCostPerMt, financingDays, validUntil, value);
    }
}
