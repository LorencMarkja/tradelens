package io.github.lorencmarkja.tradelens.proposal;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Issue(String code, Severity severity, String message, ProposalField field, Action action) {

    public enum Severity { BLOCKING, WARNING, INFO }

    /** What the user can do in the review panel to resolve a blocking issue. */
    public enum Action { SELECT_BUYER, CONFIRM_CURRENCY, CHOOSE_BASIS, CONFIRM_HANDLING, ENTER_COMBINED_VALUE }

    static Issue blocking(String code, String message, ProposalField field, Action action) {
        return new Issue(code, Severity.BLOCKING, message, field, action);
    }

    static Issue blocking(String code, String message, ProposalField field) {
        return blocking(code, message, field, null);
    }

    static Issue warning(String code, String message, ProposalField field) {
        return new Issue(code, Severity.WARNING, message, field, null);
    }

    static Issue info(String code, String message, ProposalField field) {
        return new Issue(code, Severity.INFO, message, field, null);
    }

    public boolean isBlocking() {
        return severity == Severity.BLOCKING;
    }
}
