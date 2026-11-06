package io.github.lorencmarkja.tradelens.proposal;

import java.util.List;
import java.util.Locale;

/** Vocabulary of a proposal. Values derived from a message are kept separate from user confirmations. */
public final class ProposalModel {

    private ProposalModel() {
    }

    public enum Origin { MANUAL, AI_LIVE, AI_DEMO }

    public enum Status { NEEDS_REVIEW, READY, APPLIED, DISCARDED }

    public enum Basis {
        PER_MT, TOTAL_LOT, NOT_STATED, NOT_APPLICABLE;

        static Basis fromExtraction(String s) {
            return valueOf(s.toUpperCase(Locale.ROOT));
        }
    }

    public enum Handling {
        INCLUDED, EXCLUDED, NOT_STATED, NOT_APPLICABLE;

        static Handling fromExtraction(String s) {
            return valueOf(s.toUpperCase(Locale.ROOT));
        }
    }

    /** Explicit confirmations or additions made by the user while reviewing. */
    public record UserInput(Boolean currencyConfirmedUsd, Basis basis, Boolean handlingConfirmedIncluded,
                            String combinedValue) {
        public static final UserInput NONE = new UserInput(null, null, null, null);
    }

    /** One proposed field update, as extracted (or entered manually), plus user input. */
    public record DraftChange(
            ProposalField field,
            String buyer,
            String buyerQuote,
            String value,
            String currency,
            Basis basis,
            Handling handling,
            String quote,
            String ambiguity,
            UserInput user) {

        public DraftChange withUser(UserInput user) {
            return new DraftChange(field, buyer, buyerQuote, value, currency, basis, handling, quote, ambiguity, user);
        }
    }

    public record Draft(String lotReference, String lotReferenceQuote, String notExtractedNote,
                        List<DraftChange> changes) {
    }

    public record Correction(java.time.Instant at, ProposalField field, String kind, String description) {
    }
}
