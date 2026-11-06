package io.github.lorencmarkja.tradelens.scenario;

import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Single source of truth for the synthetic demo data: used to seed an empty database
 * and by "Reset scenario". All values are invented.
 */
@Component
public class DemoSeed implements ApplicationRunner {

    public static final String LOT_CODE = "CU-104";
    private static final LocalDate OFFER_VALIDITY = LocalDate.of(2026, 11, 30);

    record SeedOffer(long id, String buyer, String salePrice, String logistics, int financingDays) {
    }

    static final List<SeedOffer> OFFERS = List.of(
            new SeedOffer(1, "Buyer A", "9300", "110", 20),
            new SeedOffer(2, "Buyer B", "9250", "60", 8),
            new SeedOffer(3, "Buyer C", "9400", "180", 40));

    private final LotRepository lots;
    private final OfferRepository offers;
    private final EntityManager entityManager;

    public DemoSeed(LotRepository lots, OfferRepository offers, EntityManager entityManager) {
        this.lots = lots;
        this.offers = offers;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!lots.existsById(LOT_CODE)) {
            lots.save(new Lot(LOT_CODE, "Copper cathodes", new BigDecimal("500"), "Rotterdam warehouse", "USD",
                    new BigDecimal("9000"), new BigDecimal("9000"), new BigDecimal("0.05"), 360));
            for (SeedOffer s : OFFERS) {
                Offer offer = new Offer(s.id(), LOT_CODE, s.buyer());
                apply(offer, s);
                offers.save(offer);
            }
        }
    }

    /** Restores offers to the demo values and clears proposals and history. Configuration is untouched. */
    @Transactional
    public void reset() {
        entityManager.createNativeQuery("DELETE FROM change_event").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM update_proposal").executeUpdate();
        entityManager.clear();
        for (SeedOffer s : OFFERS) {
            Offer offer = offers.findById(s.id()).orElseThrow();
            apply(offer, s);
        }
    }

    private static void apply(Offer offer, SeedOffer s) {
        offer.resetTo(new BigDecimal(s.salePrice()), new BigDecimal(s.logistics()), s.financingDays(), OFFER_VALIDITY);
    }
}
