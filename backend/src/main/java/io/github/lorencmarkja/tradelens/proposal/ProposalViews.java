package io.github.lorencmarkja.tradelens.proposal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.lorencmarkja.tradelens.economics.OfferStatus;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Basis;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Correction;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Handling;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Origin;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Status;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.UserInput;
import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.OfferView;
import io.github.lorencmarkja.tradelens.scenario.ScenarioViews.ScenarioResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProposalViews {

    private ProposalViews() {
    }

    public static final int MAX_MESSAGE_LENGTH = 5000;
    private static final String AMOUNT_PATTERN = "^\\d{1,9}(\\.\\d{1,6})?$";

    // ---- requests ----

    public record AnalyzeRequest(
            @NotBlank(message = "Paste a message to analyse.")
            @Size(max = MAX_MESSAGE_LENGTH, message = "The message is limited to 5,000 characters.")
            String message) {
    }

    public record ManualRequest(
            @NotNull(message = "Select an offer.") Long offerId,
            @Pattern(regexp = AMOUNT_PATTERN, message = "Enter the sale price as a number, e.g. 9300 or 9300.50.")
            String salePricePerMt,
            @Pattern(regexp = AMOUNT_PATTERN, message = "Enter the logistics cost as a number, e.g. 140 or 140.50.")
            String logisticsCostPerMt,
            @Min(value = 0, message = "Financing days must be between 0 and 365.")
            @Max(value = 365, message = "Financing days must be between 0 and 365.")
            Integer financingDays) {
    }

    public record PatchRequest(Long offerId, List<@Valid ChangeCorrection> changes) {
    }

    public record ChangeCorrection(
            @NotNull ProposalField field,
            Boolean confirmCurrencyUsd,
            Basis basis,
            Boolean confirmHandlingIncluded,
            @Pattern(regexp = AMOUNT_PATTERN, message = "Enter the combined cost as a number, e.g. 150.")
            String combinedValue) {
    }

    // ---- responses ----

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChangeView(
            ProposalField field,
            String label,
            String unit,
            String buyer,
            String extractedValue,
            String extractedCurrency,
            Basis extractedBasis,
            Handling extractedHandling,
            String quote,
            UserInput userInput,
            String currentValue,
            String proposedValue,
            String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProposalView(
            UUID id,
            Origin origin,
            Status status,
            String message,
            Long offerId,
            String buyer,
            boolean buyerSelectable,
            Long baseOfferVersion,
            Instant createdAt,
            String aiModel,
            String promptVersion,
            String lotReference,
            String originalExtraction,
            List<ChangeView> changes,
            List<Correction> corrections) {
    }

    public record Metrics(String financingCostPerMt, String marginPerMt, String totalMargin, OfferStatus status,
                          boolean highest, Integer rank) {
    }

    public record ImpactView(long offerId, String buyer, Metrics before, Metrics after, String marginPerMtChange,
                             String totalMarginChange) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProposalResponse(
            ProposalView proposal,
            boolean canApply,
            List<Issue> issues,
            ImpactView impact,
            List<OfferView> comparisonBefore,
            List<OfferView> comparisonAfter) {
    }

    public record ApplyResponse(UUID proposalId, boolean alreadyApplied, Long changeEventId, Long offerId,
                                List<ProposalField> changedFields, ScenarioResponse scenario) {
    }
}
