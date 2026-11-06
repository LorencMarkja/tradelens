package io.github.lorencmarkja.tradelens;

import io.github.lorencmarkja.tradelens.extraction.ExtractionPort;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Base for integration tests: real PostgreSQL via Testcontainers, fake AI adapter, demo data reset before each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestBase.FakeAi.class)
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11-alpine");

    static {
        POSTGRES.start();
    }

    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        ScriptedExtractionPort scriptedExtractionPort() {
            return new ScriptedExtractionPort();
        }
    }

    public record Http(int status, JsonNode body) {
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected ScriptedExtractionPort ai;
    @Autowired
    protected ExtractionPort extractionPort;

    @BeforeEach
    void resetDemo() throws Exception {
        ai.reset();
        Http r = post("/api/demo/reset", null);
        if (r.status() != 200) {
            throw new IllegalStateException("Reset failed: " + r);
        }
    }

    protected Http getJson(String url) throws Exception {
        return exchange(get(url));
    }

    protected Http post(String url, Object body) throws Exception {
        return exchange(withBody(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url), body));
    }

    protected Http patchJson(String url, Object body) throws Exception {
        return exchange(withBody(patch(url), body));
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder b, Object body) {
        if (body == null) {
            return b;
        }
        return b.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body));
    }

    private Http exchange(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Http(result.getResponse().getStatus(), text.isEmpty() ? null : json.readTree(text));
    }
}
