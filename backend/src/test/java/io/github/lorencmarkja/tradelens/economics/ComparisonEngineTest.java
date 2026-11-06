package io.github.lorencmarkja.tradelens.economics;

import io.github.lorencmarkja.tradelens.economics.ComparisonEngine.Comparison;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonEngineTest {

    static final LocalDate SCENARIO_DATE = LocalDate.of(2026, 11, 5);
    static final LocalDate VALIDITY = LocalDate.of(2026, 11, 30);
    static final LotTerms LOT = new LotTerms(bd("500"), bd("9000"), bd("9000"), bd("0.05"), 360);

    static final OfferSnapshot A = offer(1, "Buyer A", "9300", "110", 20);
    static final OfferSnapshot B = offer(2, "Buyer B", "9250", "60", 8);
    static final OfferSnapshot C = offer(3, "Buyer C", "9400", "180", 40);

    @Test
    void initialResultsOfAllOffers() {
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(A, B, C), SCENARIO_DATE);

        assertResult(c, 1, "25.00", "165.00", "82500.00");
        assertResult(c, 2, "10.00", "180.00", "90000.00");
        assertResult(c, 3, "50.00", "170.00", "85000.00");
    }

    @Test
    void buyerBIsHighestInitiallyWith90000() {
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(A, B, C), SCENARIO_DATE);

        assertThat(c.highestOfferIds()).containsExactly(2L);
        assertThat(Money.display(c.find(2).orElseThrow().economics().totalMargin())).isEqualTo("90000.00");
        assertThat(c.find(2).orElseThrow().rank()).isEqualTo(1);
        assertThat(c.find(3).orElseThrow().rank()).isEqualTo(2);
        assertThat(c.find(1).orElseThrow().rank()).isEqualTo(3);
    }

    @Test
    void buyerCLogisticsUpdateTo140MakesCHighestWith105000AndAdds20000() {
        Comparison before = ComparisonEngine.evaluate(LOT, List.of(A, B, C), SCENARIO_DATE);
        Comparison after = ComparisonEngine.evaluate(LOT, List.of(A, B, C.withLogistics(bd("140"))), SCENARIO_DATE);

        EvaluatedOffer c = after.find(3).orElseThrow();
        assertThat(Money.display(c.economics().marginPerMt())).isEqualTo("210.00");
        assertThat(Money.display(c.economics().totalMargin())).isEqualTo("105000.00");
        assertThat(Money.display(c.economics().totalMargin()
                .subtract(before.find(3).orElseThrow().economics().totalMargin()))).isEqualTo("20000.00");
        assertThat(after.highestOfferIds()).containsExactly(3L);
    }

    @Test
    void buyerCBreakEvenLogisticsAgainstBuyerBIs170() {
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(A, B, C), SCENARIO_DATE);

        EvaluatedOffer.Threshold t = c.find(3).orElseThrow().threshold();
        assertThat(t.competitorBuyer()).isEqualTo("Buyer B");
        assertThat(Money.display(t.logisticsCostPerMt())).isEqualTo("170.00");
    }

    @Test
    void thresholdIsNegativeWhenOfferCannotMatchEvenWithZeroLogistics() {
        OfferSnapshot weak = offer(1, "Buyer A", "9100", "110", 20);
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(weak, B, C), SCENARIO_DATE);

        assertThat(Money.display(c.find(1).orElseThrow().threshold().logisticsCostPerMt())).isEqualTo("-105.00");
    }

    @Test
    void tiesAreAllMarkedHighestWithTheSameRank() {
        OfferSnapshot aTied = A.withLogistics(bd("95")); // 9300 - 9000 - 95 - 25 = 180, same as Buyer B
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(aTied, B, C), SCENARIO_DATE);

        assertThat(c.highestOfferIds()).containsExactly(1L, 2L);
        assertThat(c.find(1).orElseThrow().rank()).isEqualTo(1);
        assertThat(c.find(2).orElseThrow().rank()).isEqualTo(1);
        assertThat(c.find(3).orElseThrow().rank()).isEqualTo(2);
        assertThat(c.rankingNote()).isNull();
    }

    @Test
    void marginsDifferingBelowOneCentAreRankedOnUnroundedValuesWithANote() {
        OfferSnapshot aClose = A.withLogistics(bd("94.996")); // margin 180.004 vs Buyer B 180
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(aClose, B, C), SCENARIO_DATE);

        assertThat(c.highestOfferIds()).containsExactly(1L);
        assertThat(Money.display(c.find(1).orElseThrow().economics().marginPerMt())).isEqualTo("180.00");
        assertThat(c.rankingNote()).contains("less than USD 0.01/mt");
    }

    @Test
    void zeroLogisticsZeroDaysAndNegativeMargin() {
        OfferSnapshot loss = offer(1, "Buyer A", "8900", "0", 0);
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(loss, B, C), SCENARIO_DATE);

        EvaluatedOffer e = c.find(1).orElseThrow();
        assertThat(Money.display(e.economics().financingCostPerMt())).isEqualTo("0.00");
        assertThat(Money.display(e.economics().marginPerMt())).isEqualTo("-100.00");
        assertThat(Money.display(e.economics().totalMargin())).isEqualTo("-50000.00");
        assertThat(e.status()).isEqualTo(OfferStatus.RANKED);
        assertThat(e.rank()).isEqualTo(3);
    }

    @Test
    void decimalsKeepPrecisionAndTotalUsesUnroundedMargin() {
        LotTerms lot = new LotTerms(bd("500"), bd("9000"), bd("9100"), bd("0.05"), 360);
        OfferEconomics e = MarginCalculator.compute(lot, offer(1, "Buyer A", "9300", "110", 7));

        // 9100 x 0.05 x 7 / 360 = 8.847222...
        assertThat(Money.display(e.financingCostPerMt())).isEqualTo("8.85");
        assertThat(Money.display(e.marginPerMt())).isEqualTo("181.15");
        // 181.152777... x 500 = 90576.39, not 181.15 x 500 = 90575.00
        assertThat(Money.display(e.totalMargin())).isEqualTo("90576.39");
    }

    @Test
    void presentationRoundingIsHalfUp() {
        assertThat(Money.display(bd("0.125"))).isEqualTo("0.13");
        assertThat(Money.display(bd("2.345"))).isEqualTo("2.35");
        assertThat(Money.display(bd("-0.125"))).isEqualTo("-0.13");
        assertThat(Money.display(bd("2.344999"))).isEqualTo("2.34");
    }

    @Test
    void incompleteOfferIsExcludedFromRankingAndMissingCostIsNotZero() {
        OfferSnapshot cMissing = C.withLogistics(null);
        Comparison c = ComparisonEngine.evaluate(LOT, List.of(A, B, cMissing), SCENARIO_DATE);

        EvaluatedOffer e = c.find(3).orElseThrow();
        assertThat(e.status()).isEqualTo(OfferStatus.INCOMPLETE);
        assertThat(e.economics().missingInputs()).containsExactly("Logistics cost");
        assertThat(e.economics().marginPerMt()).isNull();
        assertThat(e.economics().totalMargin()).isNull();
        assertThat(e.highest()).isFalse();
        assertThat(e.rank()).isNull();
        assertThat(e.statusReasons()).containsExactly("Logistics cost is missing");
        assertThat(c.highestOfferIds()).containsExactly(2L);
    }

    @Test
    void expiredQuoteIsExcludedFromRankingRelativeToScenarioDate() {
        OfferSnapshot cExpired = C.withLogistics(bd("140")).withLogisticsQuoteExpiry(LocalDate.of(2026, 11, 4));
        OfferSnapshot cValidToday = C.withLogistics(bd("140")).withLogisticsQuoteExpiry(SCENARIO_DATE);

        Comparison expired = ComparisonEngine.evaluate(LOT, List.of(A, B, cExpired), SCENARIO_DATE);
        assertThat(expired.find(3).orElseThrow().status()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(expired.highestOfferIds()).containsExactly(2L);

        Comparison valid = ComparisonEngine.evaluate(LOT, List.of(A, B, cValidToday), SCENARIO_DATE);
        assertThat(valid.find(3).orElseThrow().status()).isEqualTo(OfferStatus.RANKED);
        assertThat(valid.highestOfferIds()).containsExactly(3L);
    }

    private static void assertResult(Comparison c, long id, String financing, String margin, String total) {
        OfferEconomics e = c.find(id).orElseThrow().economics();
        assertThat(Money.display(e.financingCostPerMt())).isEqualTo(financing);
        assertThat(Money.display(e.marginPerMt())).isEqualTo(margin);
        assertThat(Money.display(e.totalMargin())).isEqualTo(total);
    }

    static OfferSnapshot offer(long id, String buyer, String price, String logistics, Integer days) {
        return new OfferSnapshot(id, buyer, bd(price), logistics == null ? null : bd(logistics), days, VALIDITY, null);
    }

    static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
