package io.github.lorencmarkja.tradelens.proposal;

import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Origin;
import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A proposed update awaiting review. Draft and corrections are stored as JSON text; the original extraction is never modified. */
@Entity
@Table(name = "update_proposal")
public class UpdateProposal {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private Origin origin;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(columnDefinition = "text")
    private String message;
    @Column(columnDefinition = "text")
    private String originalExtraction;
    private String aiModel;
    private String promptVersion;
    private Long offerId;
    private Long baseOfferVersion;
    @Column(columnDefinition = "text")
    private String draft;
    @Column(columnDefinition = "text")
    private String corrections;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant appliedAt;

    protected UpdateProposal() {
    }

    public UpdateProposal(UUID id, Origin origin, String message, String originalExtraction, String aiModel,
                          String promptVersion, Long offerId, Long baseOfferVersion, String draft, Instant now) {
        this.id = id;
        this.origin = origin;
        this.status = Status.NEEDS_REVIEW;
        this.message = message;
        this.originalExtraction = originalExtraction;
        this.aiModel = aiModel;
        this.promptVersion = promptVersion;
        this.offerId = offerId;
        this.baseOfferVersion = baseOfferVersion;
        this.draft = draft;
        this.corrections = "[]";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return status == Status.NEEDS_REVIEW || status == Status.READY;
    }

    public void updateReview(String draft, String corrections, Status status, Instant now) {
        this.draft = draft;
        this.corrections = corrections;
        this.status = status;
        this.updatedAt = now;
    }

    public void selectOffer(Long offerId, Long version) {
        this.offerId = offerId;
        this.baseOfferVersion = version;
    }

    public void markStatus(Status status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public void markApplied(Instant now) {
        this.status = Status.APPLIED;
        this.appliedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getOriginalExtraction() {
        return originalExtraction;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Long getOfferId() {
        return offerId;
    }

    public Long getBaseOfferVersion() {
        return baseOfferVersion;
    }

    public String getDraft() {
        return draft;
    }

    public String getCorrections() {
        return corrections;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
