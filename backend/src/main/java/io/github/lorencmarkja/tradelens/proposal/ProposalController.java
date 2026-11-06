package io.github.lorencmarkja.tradelens.proposal;

import io.github.lorencmarkja.tradelens.common.ErrorResponse;
import io.github.lorencmarkja.tradelens.extraction.AiExtractionException;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.AnalyzeRequest;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ApplyResponse;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ManualRequest;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.PatchRequest;
import io.github.lorencmarkja.tradelens.proposal.ProposalViews.ProposalResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    private final ProposalService service;

    public ProposalController(ProposalService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    public ProposalResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return service.analyze(request.message());
    }

    @PostMapping("/manual")
    public ProposalResponse manual(@Valid @RequestBody ManualRequest request) {
        return service.createManual(request);
    }

    @PatchMapping("/{id}")
    public ProposalResponse patch(@PathVariable UUID id, @Valid @RequestBody PatchRequest request) {
        return service.patch(id, request);
    }

    @GetMapping("/{id}/preview")
    public ProposalResponse preview(@PathVariable UUID id) {
        return service.preview(id);
    }

    @PostMapping("/{id}/apply")
    public ApplyResponse apply(@PathVariable UUID id) {
        return service.apply(id);
    }

    @PostMapping("/{id}/discard")
    public ProposalResponse discard(@PathVariable UUID id) {
        return service.discard(id);
    }

    @ExceptionHandler(AiExtractionException.class)
    ResponseEntity<ErrorResponse> handleExtraction(AiExtractionException e) {
        return ResponseEntity.status(e.kind().status).body(new ErrorResponse(e.kind().code, e.getMessage(), null));
    }
}
