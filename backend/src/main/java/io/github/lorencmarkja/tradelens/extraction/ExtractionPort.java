package io.github.lorencmarkja.tradelens.extraction;

import io.github.lorencmarkja.tradelens.common.AppProperties.AiMode;

import java.util.List;

/**
 * Contract shared by the demo and the live OpenAI adapters. The adapter only interprets text:
 * it never computes margins, picks a buyer or writes data.
 */
public interface ExtractionPort {

    AiMode mode();

    /** Model identifier, or null when no model is involved (demo mode). */
    String model();

    String promptVersion();

    ExtractionResult extract(String message, ExtractionContext context);

    record ExtractionContext(String lotCode, String product, List<String> buyers) {
    }

    record ExtractionResult(ExtractionOutput output, String rawJson, String model, String promptVersion) {
    }
}
