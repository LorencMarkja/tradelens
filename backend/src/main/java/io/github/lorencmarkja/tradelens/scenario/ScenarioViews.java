package io.github.lorencmarkja.tradelens.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.lorencmarkja.tradelens.economics.EvaluatedOffer;
import io.github.lorencmarkja.tradelens.economics.Money;
import io.github.lorencmarkja.tradelens.economics.OfferStatus;

import java.time.LocalDate;
import java.util.List;

/** API views. Decimal values are strings: inputs as entered, derived values rounded to cents. */
public final class ScenarioViews {

    private ScenarioViews() {
    }

    public record ScenarioResponse(
            LocalDate scenarioDate,
            AiInfo ai,
            LotView lot,
            List<OfferView> offers,
            List<Long> highestOfferIds,
            String rankingNote,
            List<SampleView> samples) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AiInfo(String mode, String label, String model, String promptVersion) {
    }

    public record SampleView(String id, String label, String message) {
    }

    public record LotView(
            String code,
            String product,
            String quantityMt,
            String location,
            String currency,
            String purchaseCostPerMt,
            String financedCapitalPerMt,
            String annualRatePercent,
            int dayCountBasis,
            String financingConvention) {

        public static LotView of(Lot lot) {
            return new LotView(lot.getCode(), lot.getProduct(), Money.input(lot.getQuantityMt()), lot.getLocation(),
                    lot.getCurrency(), Money.display(lot.getPurchaseCostPerMt()),
                    Money.display(lot.getFinancedCapitalPerMt()),
                    Money.input(lot.getAnnualRate().movePointRight(2)), lot.getDayCountBasis(),
                    "Simple interest ACT/" + lot.getDayCountBasis());
        }
    }

    public record ThresholdView(long competitorOfferId, String competitorBuyer, String competitorMarginPerMt,
                                String logisticsCostPerMt) {
    }

    public record OfferView(
            long id,
            String buyer,
            long version,
            String salePricePerMt,
            ValueSource salePriceSource,
            String logisticsCostPerMt,
            ValueSource logisticsSource,
            Integer financingDays,
            ValueSource financingDaysSource,
            LocalDate validUntil,
            LocalDate logisticsQuoteExpiry,
            String financingCostPerMt,
            String marginPerMt,
            String totalMargin,
            OfferStatus status,
            List<String> statusReasons,
            boolean highest,
            Integer rank,
            ThresholdView threshold) {

        public static OfferView of(Offer entity, EvaluatedOffer e) {
            EvaluatedOffer.Threshold t = e.threshold();
            return new OfferView(
                    entity.getId(),
                    entity.getBuyerName(),
                    entity.getVersion(),
                    Money.display(e.offer().salePricePerMt()),
                    entity.getSalePriceSource(),
                    Money.display(e.offer().logisticsCostPerMt()),
                    entity.getLogisticsSource(),
                    e.offer().financingDays(),
                    entity.getFinancingDaysSource(),
                    e.offer().validUntil(),
                    e.offer().logisticsQuoteExpiry(),
                    Money.display(e.economics().financingCostPerMt()),
                    Money.display(e.economics().marginPerMt()),
                    Money.display(e.economics().totalMargin()),
                    e.status(),
                    e.statusReasons(),
                    e.highest(),
                    e.rank(),
                    t == null ? null : new ThresholdView(t.competitorOfferId(), t.competitorBuyer(),
                            Money.display(t.competitorMarginPerMt()), Money.display(t.logisticsCostPerMt())));
        }
    }
}
