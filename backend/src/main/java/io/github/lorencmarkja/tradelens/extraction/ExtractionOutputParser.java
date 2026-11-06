package io.github.lorencmarkja.tradelens.extraction;

import io.github.lorencmarkja.tradelens.extraction.ExtractionOutput.ExtractedUpdate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/** Parses and shape-checks extraction JSON. Anything outside the schema is rejected, not repaired. */
public final class ExtractionOutputParser {

    static final Set<String> FIELDS = Set.of("sale_price", "logistics_cost", "financing_days", "quote_valid_until");
    static final Set<String> BASES = Set.of("per_mt", "total_lot", "not_stated", "not_applicable");
    static final Set<String> HANDLING = Set.of("included", "excluded", "not_stated", "not_applicable");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private ExtractionOutputParser() {
    }

    public static ExtractionOutput parse(String json) {
        ExtractionOutput output;
        try {
            output = MAPPER.readValue(json, ExtractionOutput.class);
        } catch (JacksonException e) {
            throw invalid("The AI response is not valid JSON for the extraction schema.", e);
        }
        if (output == null || output.updates() == null) {
            throw invalid("The AI response does not contain the 'updates' list.", null);
        }
        for (ExtractedUpdate u : output.updates()) {
            if (u == null || !FIELDS.contains(u.field())) {
                throw invalid("The AI response contains an unsupported field.", null);
            }
            if (u.value() == null || u.quote() == null) {
                throw invalid("The AI response contains an update without value or source text.", null);
            }
            if (!BASES.contains(u.basis()) || !HANDLING.contains(u.handling())) {
                throw invalid("The AI response contains an unsupported unit or handling value.", null);
            }
        }
        return output;
    }

    private static AiExtractionException invalid(String message, Throwable cause) {
        return new AiExtractionException(AiExtractionException.Kind.INVALID_RESPONSE, message, cause);
    }
}
