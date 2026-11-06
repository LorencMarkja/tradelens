package io.github.lorencmarkja.tradelens;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradelensApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoadsAndHealthIsUp() throws Exception {
        Http health = getJson("/actuator/health");
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.body().get("status").asString()).isEqualTo("UP");
    }

    @Test
    void usesTheFakeAiAdapterInTests() {
        assertThat(extractionPort).isSameAs(ai);
    }
}
