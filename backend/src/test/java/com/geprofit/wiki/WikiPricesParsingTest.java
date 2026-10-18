package com.geprofit.wiki;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The response is parsed for real here. The ingestion tests mock the client,
 * so they never saw the day the API sent a price too big for an int and the
 * parse failed for every item at once.
 */
class WikiPricesParsingTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("a price too big for an int still parses, and only that item's price is dropped")
    void anImpossiblePriceDoesNotBreakTheBatch() throws Exception {
        String body = """
                {"data": {
                  "2":     {"high": 200, "highTime": 1700000000, "low": 190, "lowTime": 1700000000},
                  "20011": {"high": 9100000000, "highTime": 1700000000, "low": 150, "lowTime": 1700000000}
                }}""";
        WikiPricesClient.Envelope<WikiPricesClient.LatestPoint> parsed =
                json.readValue(body, new TypeReference<>() {});

        assertThat(parsed.data()).hasSize(2);
        assertThat(WikiPricesClient.gp(parsed.data().get("2").high())).isEqualTo(200);
        assertThat(WikiPricesClient.gp(parsed.data().get("20011").high())).isNull();
        assertThat(WikiPricesClient.gp(parsed.data().get("20011").low())).isEqualTo(150);
    }

    @Test
    @DisplayName("gp keeps every price the game can hold and nothing else")
    void gpBounds() {
        assertThat(WikiPricesClient.gp((long) Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        assertThat(WikiPricesClient.gp(Integer.MAX_VALUE + 1L)).isNull();
        assertThat(WikiPricesClient.gp(-1L)).isNull();
        assertThat(WikiPricesClient.gp(null)).isNull();
    }
}
