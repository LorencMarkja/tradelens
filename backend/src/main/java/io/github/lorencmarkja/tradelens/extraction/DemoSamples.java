package io.github.lorencmarkja.tradelens.extraction;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Fixed sample messages and their canned extractions (extraction/demo-samples.json). */
@Component
public class DemoSamples {

    public record Sample(String id, String label, String message, String extractionJson) {
    }

    private final List<Sample> samples;

    public DemoSamples() {
        JsonMapper mapper = JsonMapper.builder().build();
        try (InputStream in = new ClassPathResource("extraction/demo-samples.json").getInputStream()) {
            List<Sample> loaded = new ArrayList<>();
            for (JsonNode node : mapper.readTree(in)) {
                loaded.add(new Sample(node.get("id").asString(), node.get("label").asString(),
                        node.get("message").asString(), mapper.writeValueAsString(node.get("extraction"))));
            }
            this.samples = List.copyOf(loaded);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load demo samples", e);
        }
    }

    public List<Sample> all() {
        return samples;
    }

    public Optional<Sample> match(String message) {
        String key = normalise(message);
        return samples.stream().filter(s -> normalise(s.message()).equals(key)).findFirst();
    }

    static String normalise(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }
}
