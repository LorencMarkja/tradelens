package io.github.lorencmarkja.tradelens.history;

import io.github.lorencmarkja.tradelens.proposal.ProposalModel.Origin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Application-level trace of an applied proposal (not a certified or immutable audit trail). */
@Entity
@Table(name = "change_event")
public class ChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID proposalId;
    private Long offerId;
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    private Origin origin;
    @Column(columnDefinition = "text")
    private String changes;
    @Column(columnDefinition = "text")
    private String message;
    @Column(columnDefinition = "text")
    private String originalExtraction;
    @Column(columnDefinition = "text")
    private String corrections;
    private String aiModel;
    private String promptVersion;
    private BigDecimal totalMarginBefore;
    private BigDecimal totalMarginAfter;

    protected ChangeEvent() {
    }

    public ChangeEvent(UUID proposalId, Long offerId, Instant occurredAt, Origin origin, String changes, String message,
                       String originalExtraction, String corrections, String aiModel, String promptVersion,
                       BigDecimal totalMarginBefore, BigDecimal totalMarginAfter) {
        this.proposalId = proposalId;
        this.offerId = offerId;
        this.occurredAt = occurredAt;
        this.origin = origin;
        this.changes = changes;
        this.message = message;
        this.originalExtraction = originalExtraction;
        this.corrections = corrections;
        this.aiModel = aiModel;
        this.promptVersion = promptVersion;
        this.totalMarginBefore = totalMarginBefore;
        this.totalMarginAfter = totalMarginAfter;
    }

    public Long getId() {
        return id;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public Long getOfferId() {
        return offerId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Origin getOrigin() {
        return origin;
    }

    public String getChanges() {
        return changes;
    }

    public String getMessage() {
        return message;
    }

    public String getOriginalExtraction() {
        return originalExtraction;
    }

    public String getCorrections() {
        return corrections;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public BigDecimal getTotalMarginBefore() {
        return totalMarginBefore;
    }

    public BigDecimal getTotalMarginAfter() {
        return totalMarginAfter;
    }
}
