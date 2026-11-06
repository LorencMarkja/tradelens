package io.github.lorencmarkja.tradelens.proposal;

import io.github.lorencmarkja.tradelens.economics.Money;
import io.github.lorencmarkja.tradelens.economics.OfferSnapshot;
import io.github.lorencmarkja.tradelens.proposal.Issue.Action;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Basis;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Draft;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.DraftChange;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Handling;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Origin;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.UserInput;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies a draft against the message and the current data, and normalises values (e.g. total lot cost to per mt).
 * Pure function: no persistence, no AI.
 */
public final class ProposalValidator {

    private static final Pattern AMOUNT = Pattern.compile("^\\d+(\\.\\d+)?$");
    private static final Pattern NUMBER_IN_TEXT = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern PAYMENT_TERMS = Pattern.compile("\\b(payment|pay|paid|terms)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FINANCING = Pattern.compile("financ|immobili", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_WORDS = Pattern.compile(
            "\\b(ignore|disregard|override)\\b|\\b(set|change|replace)\\s+(buyer\\s+\\w+'s|the|its|their|our)\\b[^.]{0,40}\\bto\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPERATIVE_START = Pattern.compile(
            "^\\s*(please\\s+)?(set|change|apply|make|replace|update|use)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SYSTEM_DIRECTED = Pattern.compile(
            "\\b(ignore|disregard)\\b[^.]{0,30}\\b(instructions?|rules?|prompt)\\b|\\bapply\\b[^.]{0,30}\\bimmediately\\b",
            Pattern.CASE_INSENSITIVE);
    private static final BigDecimal LARGE_PRICE_CHANGE = new BigDecimal("0.10");

    private ProposalValidator() {
    }

    public enum ExpiryTarget { LOGISTICS_QUOTE, OFFER }

    public record NormalizedChange(ProposalField field, BigDecimal amount, Integer days, LocalDate date,
                                   ExpiryTarget expiryTarget, String note) {
    }

    public record Context(
            Origin origin,
            String message,
            Draft draft,
            String lotCode,
            BigDecimal lotQuantityMt,
            List<OfferRef> offers,
            Long offerId,
            Long baseOfferVersion,
            LocalDate scenarioDate) {
    }

    public record OfferRef(long id, String buyer, long version, OfferSnapshot snapshot) {
    }

    public record Result(List<Issue> issues, List<NormalizedChange> changes) {

        public boolean hasBlocking() {
            return issues.stream().anyMatch(Issue::isBlocking);
        }

        public Optional<NormalizedChange> change(ProposalField field) {
            return changes.stream().filter(c -> c.field() == field).findFirst();
        }
    }

    public static Result validate(Context ctx) {
        List<Issue> issues = new ArrayList<>();
        List<NormalizedChange> normalized = new ArrayList<>();
        boolean fromMessage = ctx.origin() != Origin.MANUAL;
        Draft draft = ctx.draft();

        if (fromMessage) {
            checkLot(ctx, issues);
            if (ctx.message() != null && SYSTEM_DIRECTED.matcher(ctx.message()).find()) {
                issues.add(Issue.warning("SYSTEM_DIRECTED_TEXT",
                        "The message contains text addressed to the system (for example \u201cignore previous instructions\u201d). "
                                + "It was treated as message content only.", null));
            }
            if (draft.notExtractedNote() != null && !draft.notExtractedNote().isBlank()) {
                issues.add(Issue.info("NOT_EXTRACTED", draft.notExtractedNote(), null));
            }
        }

        boolean buyerProblem = checkBuyers(ctx, issues, fromMessage);
        if (issues.stream().anyMatch(i -> i.code().equals("MULTIPLE_OFFERS"))) {
            return new Result(List.copyOf(issues), List.of());
        }

        if (draft.changes().isEmpty()) {
            issues.add(Issue.blocking("NO_SUPPORTED_DATA",
                    "No supported value (sale price, logistics cost, financing days or quote expiry) was found. Nothing will be changed.",
                    null));
        } else if (ctx.offerId() == null && !buyerProblem) {
            issues.add(Issue.blocking("BUYER_REQUIRED",
                    "The message does not say which buyer it refers to. Select the offer to update.", null,
                    Action.SELECT_BUYER));
        }

        Set<ProposalField> seen = new HashSet<>();
        for (DraftChange change : draft.changes()) {
            if (!seen.add(change.field())) {
                issues.add(Issue.blocking("DUPLICATE_FIELD",
                        "The message contains more than one value for " + change.field().label().toLowerCase(Locale.ROOT)
                                + ". Import one update at a time.", change.field()));
                continue;
            }
            if (fromMessage && !inMessage(ctx.message(), change.quote())) {
                issues.add(Issue.blocking("QUOTE_NOT_IN_MESSAGE",
                        "The extracted " + change.field().label().toLowerCase(Locale.ROOT)
                                + " is not supported by the message text and was rejected.", change.field()));
                continue;
            }
            if (fromMessage && isInstruction(change.quote())) {
                issues.add(Issue.blocking("INSTRUCTION_NOT_DATA",
                        "\u201c" + change.quote() + "\u201d is phrased as an instruction, not as a quoted value. "
                                + "Instructions inside messages are never executed.", change.field()));
                continue;
            }
            NormalizedChange n = switch (change.field()) {
                case SALE_PRICE, LOGISTICS -> normalizeAmount(ctx, change, fromMessage, issues);
                case FINANCING_DAYS -> normalizeDays(change, fromMessage, issues);
                case QUOTE_VALID_UNTIL -> normalizeDate(ctx, change, fromMessage, issues);
            };
            if (n != null) {
                normalized.add(n);
            }
            if (fromMessage && change.ambiguity() != null && !change.ambiguity().isBlank()) {
                issues.add(Issue.info("EXTRACTION_NOTE", "Extraction note: " + change.ambiguity(), change.field()));
            }
        }

        attachExpiry(normalized, issues);

        OfferRef offer = findOffer(ctx);
        if (offer != null && offer.snapshot().salePricePerMt() != null) {
            normalized.stream().filter(n -> n.field() == ProposalField.SALE_PRICE).findFirst().ifPresent(n -> {
                BigDecimal current = offer.snapshot().salePricePerMt();
                BigDecimal relative = n.amount().subtract(current).abs().divide(current, 4, RoundingMode.HALF_UP);
                if (relative.compareTo(LARGE_PRICE_CHANGE) > 0) {
                    issues.add(Issue.warning("LARGE_PRICE_CHANGE",
                            "The sale price would change by " + relative.movePointRight(2).stripTrailingZeros().toPlainString()
                                    + "% (" + Money.display(current) + " \u2192 " + Money.display(n.amount())
                                    + " USD/mt). Check the source before applying.", ProposalField.SALE_PRICE));
                }
            });
        }
        if (offer != null && ctx.baseOfferVersion() != null && offer.version() != ctx.baseOfferVersion()) {
            issues.add(Issue.blocking("STALE_PROPOSAL",
                    "The offer has changed since this proposal was created. Discard it and analyse the message again.",
                    null));
        }
        if (offer != null && !normalized.isEmpty() && issues.stream().noneMatch(Issue::isBlocking)
                && normalized.stream().noneMatch(n -> changesValue(offer.snapshot(), n))) {
            issues.add(Issue.blocking("NO_EFFECTIVE_CHANGE",
                    "The proposed values are identical to the current ones. Nothing would change.", null));
        }
        return new Result(List.copyOf(issues), List.copyOf(normalized));
    }

    private static void checkLot(Context ctx, List<Issue> issues) {
        Draft draft = ctx.draft();
        if (draft.lotReference() == null || draft.lotReference().isBlank()) {
            issues.add(Issue.warning("LOT_NOT_STATED",
                    "The message does not name a lot. It is assumed to refer to " + ctx.lotCode() + ".", null));
            return;
        }
        if (!inMessage(ctx.message(), draft.lotReferenceQuote())) {
            issues.add(Issue.blocking("QUOTE_NOT_IN_MESSAGE",
                    "The extracted lot reference is not supported by the message text.", null));
            return;
        }
        if (!lotKey(draft.lotReference()).equals(lotKey(ctx.lotCode()))) {
            issues.add(Issue.blocking("LOT_MISMATCH",
                    "The message refers to lot " + draft.lotReference() + ", not " + ctx.lotCode()
                            + ". Updates for other lots cannot be imported.", null));
        }
    }

    /** Returns true when a blocking buyer problem other than "missing buyer" was found. */
    private static boolean checkBuyers(Context ctx, List<Issue> issues, boolean fromMessage) {
        if (!fromMessage) {
            return false;
        }
        Set<String> buyers = new LinkedHashSet<>();
        for (DraftChange c : ctx.draft().changes()) {
            if (c.buyer() != null && !c.buyer().isBlank()) {
                buyers.add(c.buyer().strip());
                if (!inMessage(ctx.message(), c.buyerQuote())) {
                    issues.add(Issue.blocking("QUOTE_NOT_IN_MESSAGE",
                            "The extracted buyer '" + c.buyer() + "' is not supported by the message text.", c.field()));
                    return true;
                }
            }
        }
        Set<String> keys = new LinkedHashSet<>(buyers.stream().map(ProposalValidator::buyerKey).toList());
        if (keys.size() > 1) {
            issues.add(Issue.blocking("MULTIPLE_OFFERS",
                    "The message refers to more than one offer (" + String.join(", ", buyers)
                            + "). Import one update at a time.", null));
            return true;
        }
        if (keys.size() == 1 && matchBuyer(ctx.offers(), buyers.iterator().next()).isEmpty()) {
            issues.add(Issue.blocking("UNKNOWN_BUYER",
                    "'" + buyers.iterator().next() + "' is not one of the offers for lot " + ctx.lotCode() + ".", null));
            return true;
        }
        if (keys.size() == 1 && ctx.draft().changes().stream()
                .anyMatch(c -> c.field() != ProposalField.QUOTE_VALID_UNTIL && (c.buyer() == null || c.buyer().isBlank()))) {
            // Never attribute an unlinked value to the buyer named elsewhere in the message.
            issues.add(Issue.blocking("UNLINKED_VALUE",
                    "Some values in the message are not linked to " + buyers.iterator().next()
                            + ". Nothing will be attributed by guesswork: import one update at a time.", null));
            return true;
        }
        return false;
    }

    /** True when the quoted text reads as a command addressed to the system rather than a stated value. */
    static boolean isInstruction(String quote) {
        return quote != null && (COMMAND_WORDS.matcher(quote).find() || IMPERATIVE_START.matcher(quote).find());
    }

    private static NormalizedChange normalizeAmount(Context ctx, DraftChange change, boolean fromMessage,
                                                    List<Issue> issues) {
        ProposalField field = change.field();
        UserInput user = change.user() == null ? UserInput.NONE : change.user();
        boolean combinedByUser = field == ProposalField.LOGISTICS && user.combinedValue() != null;
        String raw = combinedByUser ? user.combinedValue() : change.value();
        BigDecimal amount = parseAmount(raw);
        if (amount == null) {
            issues.add(Issue.blocking("INVALID_VALUE", "'" + raw + "' is not a valid amount.", field));
            return null;
        }
        if (fromMessage && !combinedByUser && !numberInText(amount, change.quote())) {
            issues.add(Issue.blocking("VALUE_NOT_IN_QUOTE",
                    "The extracted amount " + raw + " does not appear in the quoted text and was rejected.", field));
            return null;
        }
        boolean blocked = false;

        if (!combinedByUser) {
            String currency = change.currency() == null ? null : change.currency().strip().toUpperCase(Locale.ROOT);
            if (currency == null || currency.isEmpty()) {
                if (!Boolean.TRUE.equals(user.currencyConfirmedUsd())) {
                    issues.add(Issue.blocking("CURRENCY_MISSING",
                            "The currency is not stated. Confirm that the amount is in USD.", field,
                            Action.CONFIRM_CURRENCY));
                    blocked = true;
                }
            } else if (!currency.equals("USD")) {
                issues.add(Issue.blocking("CURRENCY_UNSUPPORTED",
                        "Amounts in " + currency + " are not supported: TradeLens works in USD only and does not convert currencies.",
                        field));
                blocked = true;
            }
        }

        Basis basis = combinedByUser ? Basis.PER_MT
                : (change.basis() == Basis.PER_MT || change.basis() == Basis.TOTAL_LOT) ? change.basis() : user.basis();
        String note = null;
        if (basis == null || basis == Basis.NOT_STATED || basis == Basis.NOT_APPLICABLE) {
            issues.add(Issue.blocking("UNIT_MISSING",
                    "The message does not say whether " + raw + " is per metric tonne or a total for the whole lot.",
                    field, Action.CHOOSE_BASIS));
            blocked = true;
        } else if (basis == Basis.TOTAL_LOT) {
            BigDecimal perMt = amount.divide(ctx.lotQuantityMt(), 6, RoundingMode.HALF_UP);
            note = "Converted from a total of USD " + Money.input(amount) + " for " + Money.input(ctx.lotQuantityMt())
                    + " mt: USD " + Money.display(perMt) + "/mt.";
            issues.add(Issue.info("CONVERTED_FROM_TOTAL", note, field));
            amount = perMt;
        }

        if (field == ProposalField.LOGISTICS) {
            if (combinedByUser) {
                note = "Combined transport and handling cost entered manually during review.";
            } else if (change.handling() == Handling.EXCLUDED) {
                issues.add(Issue.blocking("HANDLING_EXCLUDED",
                        "The quote excludes handling, so it cannot replace the combined transport and handling cost. "
                                + "Enter the combined cost per mt to continue.", field, Action.ENTER_COMBINED_VALUE));
                blocked = true;
            } else if (change.handling() != Handling.INCLUDED && !Boolean.TRUE.equals(user.handlingConfirmedIncluded())) {
                issues.add(Issue.blocking("HANDLING_UNSPECIFIED",
                        "The message does not say whether handling is included. Confirm that the amount covers transport "
                                + "and handling before it replaces the logistics cost.", field, Action.CONFIRM_HANDLING));
                blocked = true;
            }
        }

        if (field == ProposalField.SALE_PRICE && amount.signum() <= 0) {
            issues.add(Issue.blocking("INVALID_VALUE", "The sale price must be greater than zero.", field));
            blocked = true;
        }
        if (field == ProposalField.LOGISTICS && amount.signum() < 0) {
            issues.add(Issue.blocking("INVALID_VALUE", "The logistics cost cannot be negative.", field));
            blocked = true;
        }
        return blocked ? null : new NormalizedChange(field, amount, null, null, null, note);
    }

    private static NormalizedChange normalizeDays(DraftChange change, boolean fromMessage, List<Issue> issues) {
        ProposalField field = ProposalField.FINANCING_DAYS;
        if (fromMessage && change.quote() != null && PAYMENT_TERMS.matcher(change.quote()).find()
                && !FINANCING.matcher(change.quote()).find()) {
            issues.add(Issue.blocking("PAYMENT_TERMS_NOT_FINANCING_DAYS",
                    "Payment terms are not converted into financing days. Enter financing days manually if needed.",
                    field));
            return null;
        }
        String raw = change.value() == null ? "" : change.value().strip();
        if (!raw.matches("^\\d{1,3}$")) {
            issues.add(Issue.blocking("INVALID_VALUE", "Financing days must be a whole number between 0 and 365.", field));
            return null;
        }
        int days = Integer.parseInt(raw);
        if (days > 365) {
            issues.add(Issue.blocking("INVALID_VALUE", "Financing days must be a whole number between 0 and 365.", field));
            return null;
        }
        if (fromMessage && !numberInText(BigDecimal.valueOf(days), change.quote())) {
            issues.add(Issue.blocking("VALUE_NOT_IN_QUOTE",
                    "The extracted number of days does not appear in the quoted text and was rejected.", field));
            return null;
        }
        return new NormalizedChange(field, null, days, null, null, null);
    }

    private static NormalizedChange normalizeDate(Context ctx, DraftChange change, boolean fromMessage,
                                                  List<Issue> issues) {
        ProposalField field = ProposalField.QUOTE_VALID_UNTIL;
        LocalDate date;
        try {
            date = LocalDate.parse(change.value().strip());
        } catch (DateTimeParseException | NullPointerException e) {
            issues.add(Issue.blocking("INVALID_VALUE", "'" + change.value() + "' is not a valid date.", field));
            return null;
        }
        if (fromMessage && !numberInText(BigDecimal.valueOf(date.getDayOfMonth()), change.quote())) {
            issues.add(Issue.blocking("VALUE_NOT_IN_QUOTE",
                    "The extracted date does not match the quoted text and was rejected.", field));
            return null;
        }
        if (date.isBefore(ctx.scenarioDate())) {
            issues.add(Issue.warning("QUOTE_ALREADY_EXPIRED",
                    "The quote expired on " + date + ", before the scenario date " + ctx.scenarioDate()
                            + ". The offer will be excluded from the ranking.", field));
        }
        return new NormalizedChange(field, null, null, date, null, null);
    }

    /** A quote expiry is attached to the logistics quote if the message updates logistics, else to the offer price. */
    private static void attachExpiry(List<NormalizedChange> normalized, List<Issue> issues) {
        for (int i = 0; i < normalized.size(); i++) {
            NormalizedChange n = normalized.get(i);
            if (n.field() != ProposalField.QUOTE_VALID_UNTIL) {
                continue;
            }
            boolean logistics = normalized.stream().anyMatch(c -> c.field() == ProposalField.LOGISTICS);
            boolean price = normalized.stream().anyMatch(c -> c.field() == ProposalField.SALE_PRICE);
            if (logistics) {
                normalized.set(i, new NormalizedChange(n.field(), null, null, n.date(), ExpiryTarget.LOGISTICS_QUOTE,
                        "Applies to the logistics quote."));
            } else if (price) {
                normalized.set(i, new NormalizedChange(n.field(), null, null, n.date(), ExpiryTarget.OFFER,
                        "Applies to the offer validity."));
            } else {
                normalized.remove(i--);
                issues.add(Issue.blocking("EXPIRY_WITHOUT_VALUE",
                        "A validity date was found, but no price or logistics cost to attach it to.",
                        ProposalField.QUOTE_VALID_UNTIL));
            }
        }
    }

    static boolean changesValue(OfferSnapshot offer, NormalizedChange n) {
        return switch (n.field()) {
            case SALE_PRICE -> !sameAmount(offer.salePricePerMt(), n.amount());
            case LOGISTICS -> !sameAmount(offer.logisticsCostPerMt(), n.amount());
            case FINANCING_DAYS -> !Objects.equals(offer.financingDays(), n.days());
            case QUOTE_VALID_UNTIL -> n.expiryTarget() == ExpiryTarget.OFFER
                    ? !Objects.equals(offer.validUntil(), n.date())
                    : !Objects.equals(offer.logisticsQuoteExpiry(), n.date());
        };
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    static OfferRef findOffer(Context ctx) {
        if (ctx.offerId() == null) {
            return null;
        }
        return ctx.offers().stream().filter(o -> o.id() == ctx.offerId()).findFirst().orElse(null);
    }

    public static Optional<OfferRef> matchBuyer(List<OfferRef> offers, String buyer) {
        if (buyer == null || buyer.isBlank()) {
            return Optional.empty();
        }
        String key = buyerKey(buyer);
        return offers.stream().filter(o -> buyerKey(o.buyer()).equals(key)).findFirst();
    }

    static String buyerKey(String buyer) {
        String s = buyer.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return s.startsWith("buyer ") ? s.substring(6).strip() : s;
    }

    private static String lotKey(String lot) {
        return lot.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s_]", "-");
    }

    static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip().replace(",", "").replace(" ", "");
        return AMOUNT.matcher(s).matches() ? new BigDecimal(s) : null;
    }

    static boolean inMessage(String message, String quote) {
        if (message == null || quote == null || quote.isBlank()) {
            return false;
        }
        return squash(message).contains(squash(quote));
    }

    private static String squash(String s) {
        return s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static boolean numberInText(BigDecimal value, String text) {
        if (text == null) {
            return false;
        }
        Matcher m = NUMBER_IN_TEXT.matcher(text);
        while (m.find()) {
            BigDecimal found = parseAmount(m.group());
            if (found != null && found.compareTo(value) == 0) {
                return true;
            }
        }
        return false;
    }
}
