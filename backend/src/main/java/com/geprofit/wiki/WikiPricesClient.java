package com.geprofit.wiki;

import com.geprofit.config.GeProfitProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The only class that talks to the OSRS Wiki prices API.
 *
 * <p>Isolating it means the ingestion service can be tested with a fake, and
 * that the two obligations this API places on callers are honoured in exactly
 * one place:
 *
 * <ul>
 *   <li><b>Identify yourself.</b> The Wiki asks every client for a descriptive
 *       User-Agent with a contact. Generic agents get blocked, and fairly so --
 *       this is a free service run by volunteers.
 *   <li><b>Poll politely.</b> Prices update every minute at best, so polling
 *       faster only wastes someone else's bandwidth. The intervals are config.
 * </ul>
 *
 * <p>Note also that the server polls once for everybody: the dashboard and the
 * plugin both read our database, so the Wiki sees one client no matter how many
 * users we have. That is the main reason this backend exists at all.
 */
@Component
public class WikiPricesClient {

    private final RestClient http;

    public WikiPricesClient(GeProfitProperties props) {
        this.http = RestClient.builder()
                .baseUrl(props.wiki().baseUrl())
                .defaultHeader("User-Agent", props.wiki().userAgent())
                .requestFactory(timeouts(Duration.ofSeconds(props.wiki().timeoutSeconds())))
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timeouts(Duration timeout) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }

    /** The item catalogue. Large, nearly static -- fetch rarely. */
    public List<MappingItem> mapping() {
        return get("/mapping", new ParameterizedTypeReference<List<MappingItem>>() {
        });
    }

    /** Current buy/sell for every item, keyed by item id as a string. */
    public Map<String, LatestPoint> latest() {
        return get("/latest", new ParameterizedTypeReference<Envelope<LatestPoint>>() {
        }).data();
    }

    /** The last completed five-minute window: average prices and volumes. */
    public FiveMinute fiveMinute() {
        return get("/5m", new ParameterizedTypeReference<FiveMinute>() {
        });
    }

    private <T> T get(String path, ParameterizedTypeReference<T> type) {
        return http.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new WikiApiException(
                            "Wiki API " + path + " returned " + res.getStatusCode());
                })
                .body(type);
    }

    /** Most endpoints wrap their payload in {"data": {...}}. */
    public record Envelope<T>(Map<String, T> data) {
    }

    public record LatestPoint(Integer high, Long highTime, Integer low, Long lowTime) {
    }

    /** The /5m response carries its own window timestamp, which we key history on. */
    public record FiveMinute(Long timestamp, Map<String, FiveMinutePoint> data) {
    }

    public record FiveMinutePoint(Integer avgHighPrice,
                                  Long highPriceVolume,
                                  Integer avgLowPrice,
                                  Long lowPriceVolume) {
    }

    public record MappingItem(Integer id,
                              String name,
                              String examine,
                              Boolean members,
                              Integer limit,
                              Integer highalch,
                              Integer lowalch,
                              Integer value) {
    }

    public static class WikiApiException extends RuntimeException {
        public WikiApiException(String message) {
            super(message);
        }
    }
}
