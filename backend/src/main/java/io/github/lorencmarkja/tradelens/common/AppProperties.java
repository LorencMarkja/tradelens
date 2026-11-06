package io.github.lorencmarkja.tradelens.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalDate;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Ai ai, Scenario scenario) {

    public enum AiMode { DEMO, LIVE }

    public record Ai(AiMode mode, OpenAi openai) {
    }

    public record OpenAi(String apiKey, String model, Duration timeout) {

        @Override
        public String toString() {
            return "OpenAi[model=" + model + ", timeout=" + timeout + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<unset>" : "<redacted>") + "]";
        }
    }

    public record Scenario(LocalDate date) {
    }
}
