package com.geprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geprofit.domain.LatestPrice;
import com.geprofit.ingest.PriceIngestionService;
import com.geprofit.repo.ItemRepository;
import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.PriceHistoryRepository;
import com.geprofit.wiki.WikiPricesClient;
import java.time.Instant;
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
 * Ingestion against a real Postgres.
 *
 * <p>Testcontainers rather than an in-memory database, for a specific reason:
 * the guarantees being tested here are <em>database</em> guarantees. The unique
 * index that makes archiving idempotent, the foreign keys that stop orphan
 * prices, and the Flyway migration itself only exist in Postgres. H2 would
 * happily pass while the real schema was broken.
 *
 * <p>The Wiki client is mocked: this test is about what we do with the data,
 * not about the Wiki being up. Hitting a live third-party API from a test makes
 * it slow, flaky, and rude.
 */
@SpringBootTest
@Testcontainers
class IngestionIntegrationTest {

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
        // The scheduler must not run during tests -- each test drives ingestion
        // explicitly, and a background poll would race with the assertions.
        registry.add("geprofit.ingest.enabled", () -> "false");
    }

    @MockBean
    WikiPricesClient wiki;

    @Autowired
    PriceIngestionService ingestion;
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
    }

    @Test
    @DisplayName("the Flyway migration runs and the schema is usable")
    void schemaExists() {
        assertThat(items.count()).isZero();
        assertThat(prices.count()).isZero();
        assertThat(history.count()).isZero();
    }

    @Test
    void storesTheCatalogue() {
        when(wiki.mapping()).thenReturn(List.of(
                mapping(2, "Cannonball", 30, 6),
                mapping(561, "Nature rune", 12_000, 108)));

        int n = ingestion.refreshCatalogue();

        assertThat(n).isEqualTo(2);
        assertThat(items.findById(561)).get()
                .extracting(i -> i.getName()).isEqualTo("Nature rune");
    }

    @Test
    @DisplayName("re-running the catalogue updates rows instead of duplicating them")
    void catalogueIsIdempotent() {
        when(wiki.mapping()).thenReturn(List.of(mapping(2, "Cannonball", 30, 6)));
        ingestion.refreshCatalogue();

        when(wiki.mapping()).thenReturn(List.of(mapping(2, "Cannonball", 40, 6)));
        ingestion.refreshCatalogue();

        assertThat(items.count()).isEqualTo(1);
        assertThat(items.findById(2)).get()
                .extracting(i -> i.getBuyLimit()).isEqualTo(40);
    }

    @Test
    @DisplayName("prices for unknown items are skipped rather than violating the foreign key")
    void skipsPricesForUnknownItems() {
        when(wiki.mapping()).thenReturn(List.of(mapping(2, "Cannonball", 30, 6)));
        ingestion.refreshCatalogue();

        when(wiki.latest()).thenReturn(Map.of(
                "2", new WikiPricesClient.LatestPoint(200, 1_700_000_000L, 190, 1_700_000_000L),
                "99999", new WikiPricesClient.LatestPoint(5, 1_700_000_000L, 4, 1_700_000_000L)));

        int n = ingestion.refreshLatestPrices();

        assertThat(n).isEqualTo(1);
        assertThat(prices.findById(99999)).isEmpty();
        LatestPrice stored = prices.findById(2).orElseThrow();
        assertThat(stored.buyAt()).isEqualTo(190);
        assertThat(stored.sellAt()).isEqualTo(200);
    }

    @Test
    @DisplayName("archiving the same window twice does not double-count volume")
    void historyArchiveIsIdempotent() {
        when(wiki.mapping()).thenReturn(List.of(mapping(2, "Cannonball", 30, 6)));
        ingestion.refreshCatalogue();
        when(wiki.latest()).thenReturn(Map.of(
                "2", new WikiPricesClient.LatestPoint(200, 1_700_000_000L, 190, 1_700_000_000L)));
        ingestion.refreshLatestPrices();

        long windowTs = 1_700_000_100L;
        when(wiki.fiveMinute()).thenReturn(new WikiPricesClient.FiveMinute(
                windowTs, Map.of("2", new WikiPricesClient.FiveMinutePoint(200, 500L, 190, 400L))));

        int first = ingestion.archiveFiveMinuteWindow();
        int second = ingestion.archiveFiveMinuteWindow();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();      // the retry wrote nothing
        assertThat(history.count()).isEqualTo(1);
        assertThat(history.totalVolumeSince(2, Instant.ofEpochSecond(windowTs - 60)))
                .isEqualTo(900);          // 500 + 400, counted exactly once
    }

    @Test
    @DisplayName("volumes from the five-minute window land on the current price row")
    void archiveCopiesVolumesOntoLatest() {
        when(wiki.mapping()).thenReturn(List.of(mapping(2, "Cannonball", 30, 6)));
        ingestion.refreshCatalogue();
        when(wiki.latest()).thenReturn(Map.of(
                "2", new WikiPricesClient.LatestPoint(200, 1_700_000_000L, 190, 1_700_000_000L)));
        ingestion.refreshLatestPrices();
        when(wiki.fiveMinute()).thenReturn(new WikiPricesClient.FiveMinute(
                1_700_000_100L, Map.of("2", new WikiPricesClient.FiveMinutePoint(200, 500L, 190, 400L))));

        ingestion.archiveFiveMinuteWindow();

        assertThat(prices.findById(2).orElseThrow().totalVolume()).isEqualTo(900);
    }

    @Test
    @DisplayName("averages are computed from our own history, which is why we keep it")
    void averagesComeFromStoredHistory() {
        when(wiki.mapping()).thenReturn(List.of(mapping(2, "Cannonball", 30, 6)));
        ingestion.refreshCatalogue();
        when(wiki.latest()).thenReturn(Map.of(
                "2", new WikiPricesClient.LatestPoint(200, 1_700_000_000L, 190, 1_700_000_000L)));
        ingestion.refreshLatestPrices();

        // Two windows: mid-points of 195 and 205, so the average is 200.
        long base = Instant.now().minusSeconds(600).getEpochSecond();
        when(wiki.fiveMinute()).thenReturn(new WikiPricesClient.FiveMinute(
                base, Map.of("2", new WikiPricesClient.FiveMinutePoint(200, 10L, 190, 10L))));
        ingestion.archiveFiveMinuteWindow();
        when(wiki.fiveMinute()).thenReturn(new WikiPricesClient.FiveMinute(
                base + 300, Map.of("2", new WikiPricesClient.FiveMinutePoint(210, 10L, 200, 10L))));
        ingestion.archiveFiveMinuteWindow();

        Double average = history.averagePriceSince(2, Instant.now().minusSeconds(3600));

        assertThat(history.count()).isEqualTo(2);
        assertThat(average).isEqualTo(200.0);
    }

    @Test
    @DisplayName("an empty response leaves the previous data alone rather than wiping it")
    void emptyResponseIsNotDestructive() {
        when(wiki.mapping()).thenReturn(List.of(mapping(2, "Cannonball", 30, 6)));
        ingestion.refreshCatalogue();

        when(wiki.mapping()).thenReturn(List.of());
        int n = ingestion.refreshCatalogue();

        assertThat(n).isZero();
        assertThat(items.count()).isEqualTo(1);   // still there
    }

    private static WikiPricesClient.MappingItem mapping(int id, String name, int limit, int highalch) {
        return new WikiPricesClient.MappingItem(id, name, "An item.", false, limit,
                highalch, highalch * 2 / 3, highalch);
    }
}
