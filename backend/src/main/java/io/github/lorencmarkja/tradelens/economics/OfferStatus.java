package io.github.lorencmarkja.tradelens.economics;

public enum OfferStatus {
    /** All inputs present and valid at the scenario date: included in the ranking. */
    RANKED,
    /** At least one economic input is missing: excluded from the ranking. */
    INCOMPLETE,
    /** The offer or a quote it depends on has expired at the scenario date: excluded from the ranking. */
    EXPIRED
}
