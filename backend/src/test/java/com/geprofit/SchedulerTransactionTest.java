package com.geprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geprofit.ingest.PriceIngestionScheduler;
import com.geprofit.repo.ItemRepository;
import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.PriceHistoryRepository;
import com.geprofit.wiki.WikiPricesClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression test for a bug that shipped and was only caught by running the
 * thing for real.
 *
 * <p>Ingestion looked healthy — the catalogue filled, prices filled, and 1,504
 * history rows were written — but every volume on {@code latest_prices} stayed
 * null, so the flip finder's default volume filter matched nothing and the
 * dashboard was permanently empty.
 *
 * <p>The cause was Spring self-invocation: the {@code @Scheduled} methods lived
 * on the same bean as the {@code @Transactional} work, and a call from one
 * method of a bean to another does not pass through the proxy. With no
 * transaction, entities loaded inside the method were already detached, so the
 * in-place volume update was discarded. The history rows survived only because
 * {@code saveAll} carries its own transaction.
 *
 * <p><b>Why the existing integration test missed it:</b> it called
 * {@code PriceIngestionService} directly, so Spring's proxy <em>was</em>
 * involved and the transaction was real. The bug only appeared when the call
 * came from the scheduler. This test therefore drives the
 * {@link PriceIngestionScheduler}, the way production does.
 */
@SpringBootTest
@Testcontainers
class SchedulerTransactionTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("geprofit")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Enabled, because this test is specifically about the scheduler path.
        // The intervals are long so nothing fires on its own mid-assertion.
        registry.add("geprofit.ingest.enabled", () -> "true");
        registry.add("geprofit.ingest.mapping-interval-ms", () -> "3600000");
        registry.add("geprofit.ingest.latest-interval-ms", () -> "3600000");
        registry.add("geprofit.ingest.history-interval-ms", () -> "3600000");
    }

    @MockBean
    WikiPricesClient wiki;

    @Autowired
    PriceIngestionScheduler scheduler;
    @Autowired
    ItemRepository items;
    @Autowired
    LatestPriceRepository prices;
    @Autowired
    PriceHistoryRepository history;

    @BeforeEach
    void clean() {
        history.deleteAll();
        prices.deleteAll();
        items.deleteAll();

        when(wiki.mapping()).thenReturn(List.of(
                new WikiPricesClient.MappingItem(2, "Cannonball", "A ball.", false, 11_000, 6, 4, 5)));
        when(wiki.latest()).thenReturn(Map.of(
                "2", new WikiPricesClient.LatestPoint(200, 1_700_000_000L, 190, 1_700_000_000L)));
        when(wiki.fiveMinute()).thenReturn(new WikiPricesClient.FiveMinute(
                1_700_000_100L,
                Map.of("2", new WikiPricesClient.FiveMinutePoint(200, 500L, 190, 400L))));
    }

    @Test
    @DisplayName("volumes are persisted when ingestion is driven by the scheduler, not just by a direct call")
    void volumesSurviveTheSchedulerPath() {
        scheduler.mapping();
        scheduler.latest();
        scheduler.history();

        // The assertion that was silently false before the scheduler was split
        // out of the service.
        assertThat(prices.findById(2).orElseThrow().totalVolume())
                .as("volume must reach the database, not just the detached entity")
                .isEqualTo(900);
    }

    @Test
    @DisplayName("the whole scheduler path fills every table")
    void schedulerPathFillsEverything() {
        scheduler.mapping();
        scheduler.latest();
        scheduler.history();

        assertThat(items.count()).isEqualTo(1);
        assertThat(prices.count()).isEqualTo(1);
        assertThat(history.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a failing upstream call does not stop the scheduler")
    void schedulerSurvivesUpstreamFailure() {
        when(wiki.mapping()).thenThrow(new WikiPricesClient.WikiApiException("boom"));

        // Must not propagate: an exception escaping a @Scheduled method stops
        // that job for the lifetime of the process.
        scheduler.mapping();

        assertThat(items.count()).isZero();
    }
}
