package io.github.lorencmarkja.tradelens.history;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.lorencmarkja.tradelens.economics.MarginCalculator;
import io.github.lorencmarkja.tradelens.economics.Money;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Correction;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Origin;
import io.github.lorencmarkja.tradelens.scenario.Offer;
import io.github.lorencmarkja.tradelens.scenario.OfferRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryEntry(
            long id,
            UUID proposalId,
            Instant occurredAt,
            long offerId,
            String buyer,
            Origin origin,
            List<FieldChange> changes,
            String message,
            String originalExtraction,
            List<Correction> corrections,
            String aiModel,
            String promptVersion,
            String totalMarginBefore,
            String totalMarginAfter,
            String totalMarginChange) {
    }

    private final ChangeEventRepository events;
    private final OfferRepository offers;
    private final JsonMapper json;

    public HistoryController(ChangeEventRepository events, OfferRepository offers, JsonMapper json) {
        this.events = events;
        this.offers = offers;
        this.json = json;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<HistoryEntry> list() {
        Map<Long, String> buyers = offers.findAll().stream().collect(Collectors.toMap(Offer::getId, Offer::getBuyerName));
        return events.findAllByOrderByOccurredAtDescIdDesc().stream().map(e -> new HistoryEntry(
                e.getId(),
                e.getProposalId(),
                e.getOccurredAt(),
                e.getOfferId(),
                buyers.get(e.getOfferId()),
                e.getOrigin(),
                json.readValue(e.getChanges(), new TypeReference<List<FieldChange>>() {
                }),
                e.getMessage(),
                e.getOriginalExtraction(),
                json.readValue(e.getCorrections(), new TypeReference<List<Correction>>() {
                }),
                e.getAiModel(),
                e.getPromptVersion(),
                Money.display(e.getTotalMarginBefore()),
                Money.display(e.getTotalMarginAfter()),
                e.getTotalMarginBefore() == null || e.getTotalMarginAfter() == null ? null
                        : Money.display(e.getTotalMarginAfter().subtract(e.getTotalMarginBefore(), MarginCalculator.MC))
        )).toList();
    }
}
