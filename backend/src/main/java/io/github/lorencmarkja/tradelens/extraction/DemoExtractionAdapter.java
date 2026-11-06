package io.github.lorencmarkja.tradelens.extraction;

import io.github.lorencmarkja.tradelens.common.AppProperties.AiMode;

/** Returns canned extractions for the sample messages only. Never calls an external service. */
public class DemoExtractionAdapter implements ExtractionPort {

    public static final String PROMPT_VERSION = "demo-samples-v1";

    private final DemoSamples samples;

    public DemoExtractionAdapter(DemoSamples samples) {
        this.samples = samples;
    }

    @Override
    public AiMode mode() {
        return AiMode.DEMO;
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    @Override
    public ExtractionResult extract(String message, ExtractionContext context) {
        DemoSamples.Sample sample = samples.match(message).orElseThrow(() -> new AiExtractionException(
                AiExtractionException.Kind.DEMO_MESSAGE_NOT_RECOGNISED,
                "Demo mode only recognises the sample messages. Load a sample, or start the backend with AI_MODE=live to analyse other text."));
        return new ExtractionResult(ExtractionOutputParser.parse(sample.extractionJson()), sample.extractionJson(),
                null, PROMPT_VERSION);
    }
}
