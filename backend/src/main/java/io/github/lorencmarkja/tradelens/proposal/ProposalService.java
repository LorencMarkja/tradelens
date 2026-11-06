package io.github.lorencmarkja.tradelens.proposal;

import io.github.lorencmarkja.tradelens.common.ApiException;
import io.github.lorencmarkja.tradelens.common.AppProperties.AiMode;
import io.github.lorencmarkja.tradelens.economics.ComparisonEngine.Comparison;
import io.github.lorencmarkja.tradelens.economics.EvaluatedOffer;
import io.github.lorencmarkja.tradelens.economics.MarginCalculator;
import io.github.lorencmarkja.tradelens.economics.Money;
import io.github.lorencmarkja.tradelens.economics.OfferSnapshot;
import io.github.lorencmarkja.tradelens.extraction.ExtractionOutput;
import io.github.lorencmarkja.tradelens.extraction.ExtractionPort;
import io.github.lorencmarkja.tradelens.extraction.ExtractionPort.ExtractionContext;
import io.github.lorencmarkja.tradelens.extraction.ExtractionPort.ExtractionResult;
import io.github.lorencmarkja.tradelens.history.ChangeEvent;
import io.github.lorencmarkja.tradelens.history.ChangeEventRepository;
import io.github.lorencmarkja.tradelens.history.FieldChange;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Basis;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Correction;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Draft;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.DraftChange;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Handling;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Origin;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Status;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.UserInput;
import io.github.lorencmarkja.tradelens.proposal.ProposalValidator.ExpiryTarget;
import io.github.lorencmarkja.tradelens.proposal.ProposalValidator.NormalizedChange;
import io.github.lorencmarkja.tradelens.proposal.ProposalValidator.OfferRef;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ApplyResponse;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ChangeCorrection;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ChangeView;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ImpactView;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ManualRequest;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.Metrics;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.PatchRequest;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ProposalResponse;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ProposalView;
import io.github.lorencmarkja.tradelens.scenario.Lot;
import io.github.lorencmarkja.tradelens.scenario.Offer;
import io.github.lorencmarkja.tradelens.scenario.OfferRepository;
import io.github.lorencmarkja.tradelens.scenario.ScenarioService;
import io.github.lorencmarkja.tradelens.scenario.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ProposalService {

    private static final TypeReference<List<Correction>> CORRECTIONS = new TypeReference<>() {
    };

    private final ScenarioService scenario;
    private final OfferRepository offers;
    private final UpdateProposalRepository proposals;
    private final ChangeEventRepository events;
    private final ExtractionPort extraction;
    private final JsonMapper json;
    private final Clock clock;

    public ProposalService(ScenarioService scenario, OfferRepository offers, UpdateProposalRepository proposals,
                           ChangeEventRepository events, ExtractionPort extraction, JsonMapper json, Clock clock) {
        this.scenario = scenario;
        this.offers = offers;
        this.proposals = proposals;
        this.events = events;
        this.extraction = extraction;
        this.json = json;
        this.clock = clock;
    }

    /** Calls the extraction adapter outside any DB transaction, then stores the proposal. Never modifies offers. */
    public ProposalResponse analyze(String message) {
        Lot lot = scenario.lot();
        List<Offer> all = scenario.offers();
        ExtractionResult result = extraction.extract(message, new ExtractionContext(lot.getCode(), lot.getProduct(),
                all.stream().map(Offer::getBuyerName).toList()));
        ExtractionOutput out = result.output();

        List<DraftChange> changes = out.updates().stream()
                .map(u -> new DraftChange(ProposalField.fromExtraction(u.field()), u.buyer(), u.buyerQuote(), u.value(),
                        u.currency(), Basis.fromExtraction(u.basis()), Handling.fromExtraction(u.handling()), u.quote(),
                        u.ambiguity(), UserInput.NONE))
                .toList();
        Draft draft = new Draft(out.lotReference(), out.lotReferenceQuote(), out.notExtractedNote(), changes);

        Set<String> buyers = new LinkedHashSet<>();
        changes.stream().map(DraftChange::buyer).filter(b -> b != null && !b.isBlank()).forEach(buyers::add);
        Offer resolved = null;
        boolean everyValueLinked = changes.stream().allMatch(c ->
                c.field() == ProposalField.QUOTE_VALID_UNTIL || (c.buyer() != null && !c.buyer().isBlank()));
        if (everyValueLinked && buyers.stream().map(ProposalValidator::buyerKey).distinct().count() == 1) {
            long id = ProposalValidator.matchBuyer(refs(all), buyers.iterator().next()).map(OfferRef::id).orElse(-1L);
            resolved = all.stream().filter(o -> o.getId() == id).findFirst().orElse(null);
        }

        Origin origin = extraction.mode() == AiMode.LIVE ? Origin.AI_LIVE : Origin.AI_DEMO;
        UpdateProposal p = new UpdateProposal(UUID.randomUUID(), origin, message, result.rawJson(), result.model(),
                result.promptVersion(), resolved == null ? null : resolved.getId(),
                resolved == null ? null : resolved.getVersion(), write(draft), now());
        ProposalValidator.Result v = validate(p, draft, lot, all);
        p.markStatus(v.hasBlocking() ? Status.NEEDS_REVIEW : Status.READY, now());
        proposals.save(p);
        return response(p, draft, lot, all, v);
    }

    @Transactional
    public ProposalResponse createManual(ManualRequest req) {
        Lot lot = scenario.lot();
        List<Offer> all = scenario.offers();
        Offer offer = all.stream().filter(o -> o.getId().equals(req.offerId())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "The offer does not exist.", "offerId"));

        List<DraftChange> changes = new ArrayList<>();
        if (req.salePricePerMt() != null && !sameAmount(offer.getSalePricePerMt(), req.salePricePerMt())) {
            changes.add(manualChange(ProposalField.SALE_PRICE, req.salePricePerMt(), Basis.PER_MT, Handling.NOT_APPLICABLE));
        }
        if (req.logisticsCostPerMt() != null && !sameAmount(offer.getLogisticsCostPerMt(), req.logisticsCostPerMt())) {
            changes.add(manualChange(ProposalField.LOGISTICS, req.logisticsCostPerMt(), Basis.PER_MT, Handling.INCLUDED));
        }
        if (req.financingDays() != null && !req.financingDays().equals(offer.getFinancingDays())) {
            changes.add(manualChange(ProposalField.FINANCING_DAYS, String.valueOf(req.financingDays()),
                    Basis.NOT_APPLICABLE, Handling.NOT_APPLICABLE));
        }
        if (changes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_CHANGES", "Change at least one value before previewing.");
        }
        Draft draft = new Draft(null, null, null, List.copyOf(changes));
        UpdateProposal p = new UpdateProposal(UUID.randomUUID(), Origin.MANUAL, null, null, null, null,
                offer.getId(), offer.getVersion(), write(draft), now());
        ProposalValidator.Result v = validate(p, draft, lot, all);
        v.issues().stream().filter(Issue::isBlocking).findFirst().ifPresent(i -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", i.message(), requestField(i.field()));
        });
        p.markStatus(Status.READY, now());
        proposals.save(p);
        return response(p, draft, lot, all, v);
    }

    @Transactional
    public ProposalResponse patch(UUID id, PatchRequest req) {
        UpdateProposal p = proposals.findForUpdate(id).orElseThrow(ProposalService::notFound);
        if (!p.isOpen()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROPOSAL_CLOSED", "This proposal was already " +
                    p.getStatus().name().toLowerCase() + " and can no longer be edited.");
        }
        Lot lot = scenario.lot();
        List<Offer> all = scenario.offers();
        Draft draft = readDraft(p);
        List<Correction> corrections = new ArrayList<>(readCorrections(p.getCorrections()));

        if (req.offerId() != null) {
            if (p.getOrigin() == Origin.MANUAL
                    || draft.changes().stream().anyMatch(c -> c.buyer() != null && !c.buyer().isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BUYER_ALREADY_IDENTIFIED",
                        "The offer is already identified and cannot be changed.", "offerId");
            }
            Offer o = all.stream().filter(x -> x.getId().equals(req.offerId())).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OFFER_NOT_FOUND",
                            "The selected offer does not exist.", "offerId"));
            if (!o.getId().equals(p.getOfferId())) {
                p.selectOffer(o.getId(), o.getVersion());
                corrections.add(new Correction(now(), null, "BUYER_SELECTED",
                        "Offer selected manually: " + o.getBuyerName() + " (not stated in the message)."));
            }
        }

        List<DraftChange> changes = new ArrayList<>(draft.changes());
        if (req.changes() != null) {
            for (ChangeCorrection cc : req.changes()) {
                int idx = indexOf(changes, cc.field());
                DraftChange c = changes.get(idx);
                UserInput u = c.user() == null ? UserInput.NONE : c.user();
                if (Boolean.TRUE.equals(cc.confirmCurrencyUsd())) {
                    if (!isMonetary(c.field()) || (c.currency() != null && !c.currency().isBlank())) {
                        throw badCorrection("The currency was stated in the message and cannot be overridden.");
                    }
                    u = new UserInput(true, u.basis(), u.handlingConfirmedIncluded(), u.combinedValue());
                    corrections.add(new Correction(now(), c.field(), "CURRENCY_CONFIRMED",
                            "User confirmed that the amount is in USD."));
                }
                if (cc.basis() != null) {
                    if (!isMonetary(c.field()) || c.basis() == Basis.PER_MT || c.basis() == Basis.TOTAL_LOT
                            || (cc.basis() != Basis.PER_MT && cc.basis() != Basis.TOTAL_LOT)) {
                        throw badCorrection("The unit can only be chosen when the message does not state it.");
                    }
                    u = new UserInput(u.currencyConfirmedUsd(), cc.basis(), u.handlingConfirmedIncluded(), u.combinedValue());
                    corrections.add(new Correction(now(), c.field(), "BASIS_CHOSEN", cc.basis() == Basis.PER_MT
                            ? "User confirmed that the amount is per metric tonne."
                            : "User confirmed that the amount is a total for the whole lot."));
                }
                if (Boolean.TRUE.equals(cc.confirmHandlingIncluded())) {
                    if (c.field() != ProposalField.LOGISTICS || c.handling() == Handling.INCLUDED
                            || c.handling() == Handling.EXCLUDED) {
                        throw badCorrection("Handling can only be confirmed when the message does not state it.");
                    }
                    u = new UserInput(u.currencyConfirmedUsd(), u.basis(), true, u.combinedValue());
                    corrections.add(new Correction(now(), c.field(), "HANDLING_CONFIRMED",
                            "User confirmed that the amount includes handling."));
                }
                if (cc.combinedValue() != null) {
                    if (c.field() != ProposalField.LOGISTICS || c.handling() != Handling.EXCLUDED) {
                        throw badCorrection("A combined cost can only be entered when the quote excludes handling.");
                    }
                    u = new UserInput(u.currencyConfirmedUsd(), u.basis(), u.handlingConfirmedIncluded(), cc.combinedValue());
                    corrections.add(new Correction(now(), c.field(), "COMBINED_VALUE_ENTERED",
                            "User entered a combined transport and handling cost of USD " + cc.combinedValue()
                                    + "/mt (message: " + c.value() + " " + Objects.toString(c.currency(), "")
                                    + ", excluding handling)."));
                }
                changes.set(idx, c.withUser(u));
            }
        }
        Draft updated = new Draft(draft.lotReference(), draft.lotReferenceQuote(), draft.notExtractedNote(),
                List.copyOf(changes));
        ProposalValidator.Result v = validate(p, updated, lot, all);
        p.updateReview(write(updated), write(corrections), v.hasBlocking() ? Status.NEEDS_REVIEW : Status.READY, now());
        return response(p, updated, lot, all, v);
    }

    @Transactional(readOnly = true)
    public ProposalResponse preview(UUID id) {
        UpdateProposal p = proposals.findById(id).orElseThrow(ProposalService::notFound);
        Lot lot = scenario.lot();
        List<Offer> all = scenario.offers();
        Draft draft = readDraft(p);
        return response(p, draft, lot, all, validate(p, draft, lot, all));
    }

    /**
     * Applies a valid proposal in one transaction. The proposal row is locked, so a second confirmation
     * waits and then returns the existing result instead of applying the change again.
     */
    @Transactional
    public ApplyResponse apply(UUID id) {
        UpdateProposal p = proposals.findForUpdate(id).orElseThrow(ProposalService::notFound);
        if (p.getStatus() == Status.APPLIED) {
            ChangeEvent existing = events.findByProposalId(p.getId()).orElse(null);
            return new ApplyResponse(p.getId(), true, existing == null ? null : existing.getId(), p.getOfferId(),
                    List.of(), scenario.view());
        }
        if (p.getStatus() == Status.DISCARDED) {
            throw new ApiException(HttpStatus.CONFLICT, "PROPOSAL_DISCARDED", "This proposal was discarded.");
        }
        Lot lot = scenario.lot();
        List<Offer> all = scenario.offers();
        Offer offer = p.getOfferId() == null ? null
                : all.stream().filter(o -> o.getId().equals(p.getOfferId())).findFirst().orElse(null);
        if (offer == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "BUYER_REQUIRED", "Select the offer to update first.");
        }
        if (!Objects.equals(offer.getVersion(), p.getBaseOfferVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_PROPOSAL",
                    "The offer has changed since this proposal was created. Discard it and analyse the message again.");
        }
        Draft draft = readDraft(p);
        ProposalValidator.Result v = validate(p, draft, lot, all);
        v.issues().stream().filter(Issue::isBlocking).findFirst().ifPresent(i -> {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PROPOSAL_NOT_READY", i.message());
        });

        Comparison before = evaluate(lot, all, null, List.of());
        BigDecimal totalBefore = before.find(offer.getId()).orElseThrow().economics().totalMargin();

        ValueSource source = p.getOrigin() == Origin.MANUAL ? ValueSource.MANUAL_EDIT : ValueSource.MESSAGE_CONFIRMED;
        OfferSnapshot old = offer.snapshot();
        List<FieldChange> fieldChanges = new ArrayList<>();
        for (NormalizedChange n : v.changes()) {
            if (!ProposalValidator.changesValue(old, n)) {
                continue;
            }
            String label = label(n.field(), n.expiryTarget());
            switch (n.field()) {
                case SALE_PRICE -> {
                    fieldChanges.add(new FieldChange(n.field(), label, n.field().unit(),
                            Money.display(old.salePricePerMt()), Money.display(n.amount())));
                    offer.updateSalePrice(n.amount(), source);
                }
                case LOGISTICS -> {
                    fieldChanges.add(new FieldChange(n.field(), label, n.field().unit(),
                            Money.display(old.logisticsCostPerMt()), Money.display(n.amount())));
                    offer.updateLogistics(n.amount(), source);
                }
                case FINANCING_DAYS -> {
                    fieldChanges.add(new FieldChange(n.field(), label, n.field().unit(),
                            Objects.toString(old.financingDays(), null), String.valueOf(n.days())));
                    offer.updateFinancingDays(n.days(), source);
                }
                case QUOTE_VALID_UNTIL -> {
                    if (n.expiryTarget() == ExpiryTarget.OFFER) {
                        fieldChanges.add(new FieldChange(n.field(), label, "", Objects.toString(old.validUntil(), null),
                                n.date().toString()));
                        offer.updateValidUntil(n.date());
                    } else {
                        fieldChanges.add(new FieldChange(n.field(), label, "",
                                Objects.toString(old.logisticsQuoteExpiry(), null), n.date().toString()));
                        offer.updateLogisticsQuoteExpiry(n.date());
                    }
                }
            }
        }
        offers.saveAndFlush(offer);
        BigDecimal totalAfter = MarginCalculator.compute(lot.terms(), offer.snapshot()).totalMargin();

        ChangeEvent event = events.save(new ChangeEvent(p.getId(), offer.getId(), now(), p.getOrigin(),
                write(fieldChanges), p.getMessage(), p.getOriginalExtraction(), p.getCorrections(), p.getAiModel(),
                p.getPromptVersion(), totalBefore, totalAfter));
        p.markApplied(now());
        return new ApplyResponse(p.getId(), false, event.getId(), offer.getId(),
                fieldChanges.stream().map(FieldChange::field).distinct().toList(), scenario.view());
    }

    @Transactional
    public ProposalResponse discard(UUID id) {
        UpdateProposal p = proposals.findForUpdate(id).orElseThrow(ProposalService::notFound);
        if (p.getStatus() == Status.APPLIED) {
            throw new ApiException(HttpStatus.CONFLICT, "PROPOSAL_ALREADY_APPLIED",
                    "This proposal was already applied and cannot be discarded.");
        }
        if (p.getStatus() != Status.DISCARDED) {
            p.markStatus(Status.DISCARDED, now());
        }
        Lot lot = scenario.lot();
        List<Offer> all = scenario.offers();
        Draft draft = readDraft(p);
        return response(p, draft, lot, all, validate(p, draft, lot, all));
    }

    // ---- helpers ----

    private ProposalValidator.Result validate(UpdateProposal p, Draft draft, Lot lot, List<Offer> all) {
        return ProposalValidator.validate(new ProposalValidator.Context(p.getOrigin(), p.getMessage(), draft,
                lot.getCode(), lot.getQuantityMt(), refs(all), p.getOfferId(), p.getBaseOfferVersion(),
                scenario.scenarioDate()));
    }

    private ProposalResponse response(UpdateProposal p, Draft draft, Lot lot, List<Offer> all,
                                      ProposalValidator.Result v) {
        Offer offer = p.getOfferId() == null ? null
                : all.stream().filter(o -> o.getId().equals(p.getOfferId())).findFirst().orElse(null);
        boolean hasLogistics = draft.changes().stream().anyMatch(c -> c.field() == ProposalField.LOGISTICS);

        List<ChangeView> changeViews = draft.changes().stream().map(c -> {
            NormalizedChange n = v.change(c.field()).orElse(null);
            ExpiryTarget target = n != null ? n.expiryTarget()
                    : c.field() == ProposalField.QUOTE_VALID_UNTIL
                    ? (hasLogistics ? ExpiryTarget.LOGISTICS_QUOTE : ExpiryTarget.OFFER) : null;
            return new ChangeView(c.field(), label(c.field(), target), c.field().unit(), c.buyer(), c.value(),
                    c.currency(), c.basis(), c.handling(), c.quote(), c.user(),
                    offer == null ? null : currentValue(offer.snapshot(), c.field(), target),
                    n == null ? null : proposedValue(n), n == null ? null : n.note());
        }).toList();

        boolean buyerSelectable = p.getOrigin() != Origin.MANUAL
                && draft.changes().stream().noneMatch(c -> c.buyer() != null && !c.buyer().isBlank())
                && !draft.changes().isEmpty();
        ProposalView view = new ProposalView(p.getId(), p.getOrigin(), p.getStatus(), p.getMessage(), p.getOfferId(),
                offer == null ? null : offer.getBuyerName(), buyerSelectable, p.getBaseOfferVersion(), p.getCreatedAt(),
                p.getAiModel(), p.getPromptVersion(), draft.lotReference(), p.getOriginalExtraction(), changeViews,
                readCorrections(p.getCorrections()));

        boolean canApply = p.isOpen() && offer != null && !v.hasBlocking();
        if (!canApply) {
            return new ProposalResponse(view, false, v.issues(), null, null, null);
        }
        Comparison before = evaluate(lot, all, null, List.of());
        Comparison after = evaluate(lot, all, offer.getId(), v.changes());
        EvaluatedOffer b = before.find(offer.getId()).orElseThrow();
        EvaluatedOffer a = after.find(offer.getId()).orElseThrow();
        ImpactView impact = new ImpactView(offer.getId(), offer.getBuyerName(), metrics(b), metrics(a),
                diff(a.economics().marginPerMt(), b.economics().marginPerMt()),
                diff(a.economics().totalMargin(), b.economics().totalMargin()));
        return new ProposalResponse(view, true, v.issues(), impact,
                ScenarioService.offerViews(all, before), ScenarioService.offerViews(all, after));
    }

    private Comparison evaluate(Lot lot, List<Offer> all, Long changedOfferId, List<NormalizedChange> changes) {
        List<OfferSnapshot> snapshots = all.stream().map(o -> {
            OfferSnapshot s = o.snapshot();
            if (changedOfferId != null && o.getId().equals(changedOfferId)) {
                for (NormalizedChange n : changes) {
                    s = applyTo(s, n);
                }
            }
            return s;
        }).toList();
        return scenario.evaluate(lot, snapshots);
    }

    private static OfferSnapshot applyTo(OfferSnapshot s, NormalizedChange n) {
        return switch (n.field()) {
            case SALE_PRICE -> s.withSalePrice(n.amount());
            case LOGISTICS -> s.withLogistics(n.amount());
            case FINANCING_DAYS -> s.withFinancingDays(n.days());
            case QUOTE_VALID_UNTIL -> n.expiryTarget() == ExpiryTarget.OFFER
                    ? s.withValidUntil(n.date()) : s.withLogisticsQuoteExpiry(n.date());
        };
    }

    private static Metrics metrics(EvaluatedOffer e) {
        return new Metrics(Money.display(e.economics().financingCostPerMt()), Money.display(e.economics().marginPerMt()),
                Money.display(e.economics().totalMargin()), e.status(), e.highest(), e.rank());
    }

    private static String diff(BigDecimal after, BigDecimal before) {
        return after == null || before == null ? null : Money.display(after.subtract(before, MarginCalculator.MC));
    }

    private static String label(ProposalField field, ExpiryTarget target) {
        if (field != ProposalField.QUOTE_VALID_UNTIL || target == null) {
            return field.label();
        }
        return target == ExpiryTarget.LOGISTICS_QUOTE ? "Logistics quote valid until" : "Offer valid until";
    }

    private static String currentValue(OfferSnapshot s, ProposalField field, ExpiryTarget target) {
        return switch (field) {
            case SALE_PRICE -> Money.display(s.salePricePerMt());
            case LOGISTICS -> Money.display(s.logisticsCostPerMt());
            case FINANCING_DAYS -> Objects.toString(s.financingDays(), null);
            case QUOTE_VALID_UNTIL -> Objects.toString(target == ExpiryTarget.OFFER ? s.validUntil()
                    : s.logisticsQuoteExpiry(), null);
        };
    }

    private static String proposedValue(NormalizedChange n) {
        return switch (n.field()) {
            case SALE_PRICE, LOGISTICS -> Money.display(n.amount());
            case FINANCING_DAYS -> String.valueOf(n.days());
            case QUOTE_VALID_UNTIL -> n.date().toString();
        };
    }

    private static List<OfferRef> refs(List<Offer> all) {
        return all.stream().map(o -> new OfferRef(o.getId(), o.getBuyerName(), o.getVersion(), o.snapshot())).toList();
    }

    private static DraftChange manualChange(ProposalField field, String value, Basis basis, Handling handling) {
        return new DraftChange(field, null, null, value, "USD", basis, handling, null, null, UserInput.NONE);
    }

    private static boolean sameAmount(BigDecimal current, String value) {
        BigDecimal parsed = ProposalValidator.parseAmount(value);
        return current != null && parsed != null && current.compareTo(parsed) == 0;
    }

    private static boolean isMonetary(ProposalField f) {
        return f == ProposalField.SALE_PRICE || f == ProposalField.LOGISTICS;
    }

    private static int indexOf(List<DraftChange> changes, ProposalField field) {
        for (int i = 0; i < changes.size(); i++) {
            if (changes.get(i).field() == field) {
                return i;
            }
        }
        throw badCorrection("The proposal does not contain a value for " + field.label().toLowerCase() + ".");
    }

    private static String requestField(ProposalField field) {
        if (field == null) {
            return null;
        }
        return switch (field) {
            case SALE_PRICE -> "salePricePerMt";
            case LOGISTICS -> "logisticsCostPerMt";
            case FINANCING_DAYS -> "financingDays";
            case QUOTE_VALID_UNTIL -> null;
        };
    }

    private static ApiException badCorrection(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CORRECTION", message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "Proposal not found.");
    }

    private Instant now() {
        return clock.instant();
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }

    private Draft readDraft(UpdateProposal p) {
        return json.readValue(p.getDraft(), Draft.class);
    }

    List<Correction> readCorrections(String text) {
        return json.readValue(text, CORRECTIONS);
    }
}
