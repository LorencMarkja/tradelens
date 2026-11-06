package io.github.lorencmarkja.tradelens.economics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/** Ranks offers on unrounded estimated margin per mt. Ties share the "highest" flag and rank. */
public final class ComparisonEngine {

    private ComparisonEngine() {
    }

    public record Comparison(List<EvaluatedOffer> offers, String rankingNote) {

        public Optional<EvaluatedOffer> find(long offerId) {
            return offers.stream().filter(o -> o.offer().offerId() == offerId).findFirst();
        }

        public List<Long> highestOfferIds() {
            return offers.stream().filter(EvaluatedOffer::highest).map(o -> o.offer().offerId()).toList();
        }
    }

    public static Comparison evaluate(LotTerms lot, List<OfferSnapshot> offers, LocalDate scenarioDate) {
        record Pre(OfferSnapshot offer, OfferEconomics economics, OfferStatus status, List<String> reasons) {
        }

        List<Pre> pre = new ArrayList<>();
        for (OfferSnapshot offer : offers) {
            OfferEconomics economics = MarginCalculator.compute(lot, offer);
            List<String> reasons = new ArrayList<>();
            economics.missingInputs().forEach(m -> reasons.add(m + " is missing"));
            boolean expired = false;
            if (offer.validUntil() != null && offer.validUntil().isBefore(scenarioDate)) {
                reasons.add("Offer validity expired on " + offer.validUntil());
                expired = true;
            }
            if (offer.logisticsQuoteExpiry() != null && offer.logisticsQuoteExpiry().isBefore(scenarioDate)) {
                reasons.add("Logistics quote expired on " + offer.logisticsQuoteExpiry());
                expired = true;
            }
            OfferStatus status = !economics.complete() ? OfferStatus.INCOMPLETE
                    : expired ? OfferStatus.EXPIRED : OfferStatus.RANKED;
            pre.add(new Pre(offer, economics, status, List.copyOf(reasons)));
        }

        TreeSet<BigDecimal> distinctMargins = new TreeSet<>(Comparator.reverseOrder());
        pre.stream()
                .filter(p -> p.status() == OfferStatus.RANKED)
                .forEach(p -> distinctMargins.add(p.economics().marginPerMt()));
        List<BigDecimal> distinct = List.copyOf(distinctMargins);
        BigDecimal best = distinct.isEmpty() ? null : distinct.getFirst();

        List<EvaluatedOffer> result = new ArrayList<>();
        for (Pre p : pre) {
            boolean ranked = p.status() == OfferStatus.RANKED;
            Integer rank = null;
            boolean highest = false;
            if (ranked) {
                BigDecimal margin = p.economics().marginPerMt();
                rank = 1 + (int) distinct.stream().filter(m -> m.compareTo(margin) > 0).count();
                highest = margin.compareTo(best) == 0;
            }
            result.add(new EvaluatedOffer(p.offer(), p.economics(), p.status(), p.reasons(), highest, rank,
                    threshold(lot, p.offer(), p.economics(), pre.stream()
                            .filter(o -> o.status() == OfferStatus.RANKED && o.offer().offerId() != p.offer().offerId())
                            .map(o -> new EvaluatedOffer(o.offer(), o.economics(), o.status(), o.reasons(), false, null, null))
                            .toList())));
        }

        String note = null;
        if (distinct.size() >= 2 && Money.round(distinct.get(0)).compareTo(Money.round(distinct.get(1))) == 0) {
            note = "The two highest margins differ by less than USD 0.01/mt; the ranking uses unrounded values.";
        }
        return new Comparison(List.copyOf(result), note);
    }

    private static EvaluatedOffer.Threshold threshold(LotTerms lot, OfferSnapshot offer, OfferEconomics economics,
                                                      List<EvaluatedOffer> competitors) {
        if (offer.salePricePerMt() == null || economics.financingCostPerMt() == null || competitors.isEmpty()) {
            return null;
        }
        EvaluatedOffer bestCompetitor = competitors.stream()
                .max(Comparator.comparing((EvaluatedOffer c) -> c.economics().marginPerMt())
                        .thenComparing(c -> -c.offer().offerId()))
                .orElseThrow();
        BigDecimal competingMargin = bestCompetitor.economics().marginPerMt();
        return new EvaluatedOffer.Threshold(
                bestCompetitor.offer().offerId(),
                bestCompetitor.offer().buyer(),
                competingMargin,
                MarginCalculator.logisticsThreshold(lot, offer.salePricePerMt(), economics.financingCostPerMt(),
                        competingMargin));
    }
}
