package io.github.lorencmarkja.tradelens.extraction;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.github.lorencmarkja.tradelens.common.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ExtractionConfig {

    /** Selects the adapter from AI_MODE. Live mode fails fast without key or model; there is no silent fallback to demo. */
    @Bean
    ExtractionPort extractionPort(AppProperties properties, DemoSamples samples) {
        AppProperties.Ai ai = properties.ai();
        if (ai.mode() == AppProperties.AiMode.LIVE) {
            AppProperties.OpenAi openai = ai.openai();
            if (openai == null || isBlank(openai.apiKey()) || isBlank(openai.model())) {
                throw new IllegalStateException("AI_MODE=live requires OPENAI_API_KEY and OPENAI_MODEL to be set.");
            }
            Duration timeout = openai.timeout() != null ? openai.timeout() : Duration.ofSeconds(30);
            return new OpenAiExtractionAdapter(OpenAIOkHttpClient.builder()
                    .apiKey(openai.apiKey())
                    .timeout(timeout)
                    .maxRetries(0)
                    .build(), openai.model());
        }
        return new DemoExtractionAdapter(samples);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
