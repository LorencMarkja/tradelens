package io.github.lorencmarkja.tradelens.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Mirror of extraction/v1/schema.json. Every value is a claim to be verified by the backend. */
public record ExtractionOutput(
        @JsonProperty("lot_reference") String lotReference,
        @JsonProperty("lot_reference_quote") String lotReferenceQuote,
        @JsonProperty("updates") List<ExtractedUpdate> updates,
        @JsonProperty("not_extracted_note") String notExtractedNote) {

    public record ExtractedUpdate(
            @JsonProperty("buyer") String buyer,
            @JsonProperty("buyer_quote") String buyerQuote,
            @JsonProperty("field") String field,
            @JsonProperty("value") String value,
            @JsonProperty("currency") String currency,
            @JsonProperty("basis") String basis,
            @JsonProperty("handling") String handling,
            @JsonProperty("quote") String quote,
            @JsonProperty("ambiguity") String ambiguity) {
    }
}
