package io.github.lorencmarkja.tradelens.scenario;

import io.github.lorencmarkja.tradelens.common.AppProperties;
import io.github.lorencmarkja.tradelens.economics.ComparisonEngine;
import io.github.lorencmarkja.tradelens.economics.ComparisonEngine.Comparison;
import io.github.lorencmarkja.tradelens.economics.OfferSnapshot;
import io.github.lorencmarkja.tradelens.extraction.ExtractionPort;
import io.github.lorencmarkja.tradelens.extraction.DemoSamples;
import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.AiInfo;
import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.LotView;
import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.OfferView;
import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.SampleView;
import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.ScenarioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ScenarioService {

    private final LotRepository lots;
    private final OfferRepository offers;
    private final AppProperties properties;
    private final ExtractionPort extraction;
    private final DemoSamples samples;

    public ScenarioService(LotRepository lots, OfferRepository offers, AppProperties properties,
                           ExtractionPort extraction, DemoSamples samples) {
        this.lots = lots;
        this.offers = offers;
        this.properties = properties;
        this.extraction = extraction;
        this.samples = samples;
    }

    public LocalDate scenarioDate() {
        return properties.scenario().date();
    }

    public Lot lot() {
        return lots.findById(DemoSeed.LOT_CODE).orElseThrow();
    }

    public List<Offer> offers() {
        return offers.findByLotCodeOrderByIdAsc(DemoSeed.LOT_CODE);
    }

    public Comparison evaluate(Lot lot, List<OfferSnapshot> snapshots) {
        return ComparisonEngine.evaluate(lot.terms(), snapshots, scenarioDate());
    }

    @Transactional(readOnly = true)
    public ScenarioResponse view() {
        Lot lot = lot();
        List<Offer> all = offers();
        Comparison comparison = evaluate(lot, all.stream().map(Offer::snapshot).toList());
        return new ScenarioResponse(
                scenarioDate(),
                aiInfo(),
                LotView.of(lot),
                offerViews(all, comparison),
                comparison.highestOfferIds(),
                comparison.rankingNote(),
                samples.all().stream().map(s -> new SampleView(s.id(), s.label(), s.message())).toList());
    }

    public static List<OfferView> offerViews(List<Offer> entities, Comparison comparison) {
        return entities.stream()
                .map(o -> OfferView.of(o, comparison.find(o.getId()).orElseThrow()))
                .toList();
    }

    public AiInfo aiInfo() {
        return switch (extraction.mode()) {
            case DEMO -> new AiInfo("demo", "Demo extraction — no live AI call", null, extraction.promptVersion());
            case LIVE -> new AiInfo("live", "Live AI extraction (OpenAI)", extraction.model(), extraction.promptVersion());
        };
    }
}
