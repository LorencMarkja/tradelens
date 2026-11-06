package io.github.lorencmarkja.tradelens.history;

import io.github.lorencmarkja.tradelens.proposal.ProposalField;

/** One field change recorded in the history, with presented (rounded) values. */
public record FieldChange(ProposalField field, String label, String unit, String before, String after) {
}
