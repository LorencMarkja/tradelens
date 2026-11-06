package io.github.lorencmarkja.tradelens;

import io.github.lorencmarkja.tradelens.common.AppProperties.AiMode;
import io.github.lorencmarkja.tradelens.extraction.AiExtractionException;
import io.github.lorencmarkja.tradelens.extraction.DemoExtractionAdapter;
import io.github.lorencmarkja.tradelens.extraction.DemoSamples;
import io.github.lorencmarkja.tradelens.extraction.ExtractionOutputParser;
import io.github.lorencmarkja.tradelens.extraction.ExtractionPort;

/**
 * Test double for the AI adapter: delegates to the demo samples unless a test scripts a response or failure.
 * No paid API calls are made by the test suite.
 */
public class ScriptedExtractionPort implements ExtractionPort {

    private final DemoExtractionAdapter demo = new DemoExtractionAdapter(new DemoSamples());
    private volatile String scriptedJson;
    private volatile AiExtractionException scriptedFailure;

    public void respondWith(String json) {
        this.scriptedJson = json;
        this.scriptedFailure = null;
    }

    public void failWith(AiExtractionException.Kind kind) {
        this.scriptedFailure = new AiExtractionException(kind, "Scripted " + kind);
        this.scriptedJson = null;
    }

    public void reset() {
        this.scriptedJson = null;
        this.scriptedFailure = null;
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
        return DemoExtractionAdapter.PROMPT_VERSION;
    }

    @Override
    public ExtractionResult extract(String message, ExtractionContext context) {
        if (scriptedFailure != null) {
            throw scriptedFailure;
        }
        if (scriptedJson != null) {
            return new ExtractionResult(ExtractionOutputParser.parse(scriptedJson), scriptedJson, "fake-model", "test");
        }
        return demo.extract(message, context);
    }
}
