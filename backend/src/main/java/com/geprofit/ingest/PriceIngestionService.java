package com.geprofit.ingest;

import com.geprofit.config.GeProfitProperties;
import com.geprofit.domain.Item;
import com.geprofit.domain.LatestPrice;
import com.geprofit.domain.PriceHistory;
import com.geprofit.repo.ItemRepository;
import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.PriceHistoryRepository;
import com.geprofit.wiki.WikiPricesClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pulls prices from the Wiki on a schedule and keeps our database current.
 *
 * <p>This is the reason the backend exists. Both clients -- the dashboard and
 * the RuneLite plugin -- read our database instead of the Wiki, so the Wiki
 * sees a single polite poller regardless of how many users are connected, and
 * we accumulate the price history the API itself does not serve.
 *
 * <p>Three jobs on three schedules, because the data changes at three
 * different rates: the catalogue is nearly static (daily), current prices move
 * constantly (every minute), and the five-minute windows are what we archive
 * as history.
 *
 * <p>Each job is defensive in the same way: a failure is logged and swallowed
 * so the scheduler keeps running. A missed poll is a gap in the data; an
 * exception escaping a scheduled method silently stops that job forever, which
 * is far worse.
 */
@Service
public class PriceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PriceIngestionService.class);

    private final WikiPricesClient wiki;
    private final ItemRepository items;
    private final LatestPriceRepository latestPrices;
    private final PriceHistoryRepository history;
    private final GeProfitProperties props;

    public PriceIngestionService(WikiPricesClient wiki,
                                 ItemRepository items,
                                 LatestPriceRepository latestPrices,
                                 PriceHistoryRepository history,
                                 GeProfitProperties props) {
        this.wiki = wiki;
        this.items = items;
        this.latestPrices = latestPrices;
        this.history = history;
        this.props = props;
    }

    // ---- scheduled entry points --------------------------------------------

    @Scheduled(initialDelay = 0,
            fixedDelayString = "${geprofit.ingest.mapping-interval-ms}")
    public void scheduledMapping() {
        if (!enabled()) {
            return;
        }
        try {
            int n = refreshCatalogue();
            log.info("Catalogue refreshed: {} items", n);
        } catch (Exception e) {
            log.warn("Catalogue refresh failed; keeping the previous catalogue", e);
        }
    }

    @Scheduled(initialDelay = 5_000,
            fixedDelayString = "${geprofit.ingest.latest-interval-ms}")
    public void scheduledLatest() {
        if (!enabled()) {
            return;
        }
        try {
            int n = refreshLatestPrices();
            log.debug("Latest prices refreshed: {} items", n);
        } catch (Exception e) {
            log.warn("Latest price refresh failed; serving the previous prices", e);
        }
    }

    @Scheduled(initialDelay = 15_000,
            fixedDelayString = "${geprofit.ingest.history-interval-ms}")
    public void scheduledHistory() {
        if (!enabled()) {
            return;
        }
        try {
            int n = archiveFiveMinuteWindow();
            log.debug("Archived {} history rows", n);
        } catch (Exception e) {
            log.warn("History archive failed; this window is a gap", e);
        }
    }

    /**
     * Turning ingestion off lets a second instance run as a read-only replica
     * without two pollers hammering the Wiki.
     */
    private boolean enabled() {
        return props.ingest().enabled();
    }

    // ---- the work ----------------------------------------------------------

    /**
     * Upsert the item catalogue.
     *
     * <p>Existing rows are updated rather than deleted and reinserted, because
     * price history has a foreign key to items and a delete would cascade it
     * away. Losing weeks of history to a catalogue refresh would be a
     * spectacular own goal.
     */
    @Transactional
    public int refreshCatalogue() {
        List<WikiPricesClient.MappingItem> mapping = wiki.mapping();
        if (mapping == null || mapping.isEmpty()) {
            log.warn("Mapping response was empty; leaving the catalogue alone");
            return 0;
        }

        Map<Integer, Item> existing = new HashMap<>();
        items.findAll().forEach(i -> existing.put(i.getId(), i));

        List<Item> toSave = new ArrayList<>();
        for (WikiPricesClient.MappingItem m : mapping) {
            if (m.id() == null || m.name() == null) {
                continue;
            }
            boolean members = Boolean.TRUE.equals(m.members());
            Item item = existing.get(m.id());
            if (item == null) {
                toSave.add(new Item(m.id(), m.name(), m.examine(), members,
                        m.limit(), m.highalch(), m.lowalch(), m.value()));
            } else {
                item.updateFrom(m.name(), m.examine(), members,
                        m.limit(), m.highalch(), m.lowalch(), m.value());
                toSave.add(item);
            }
        }
        items.saveAll(toSave);
        return toSave.size();
    }

    /**
     * Overwrite the current price of every item.
     *
     * <p>Prices for items we have no catalogue row for are skipped: the
     * latest_prices table has a foreign key to items, and the Wiki occasionally
     * prices an item before it appears in the mapping. Skipping is right --
     * inventing a catalogue row to satisfy the constraint would put an item
     * with no name in front of users.
     */
    @Transactional
    public int refreshLatestPrices() {
        Map<String, WikiPricesClient.LatestPoint> latest = wiki.latest();
        if (latest == null || latest.isEmpty()) {
            log.warn("Latest response was empty; keeping the previous prices");
            return 0;
        }

        Map<Integer, LatestPrice> existing = new HashMap<>();
        latestPrices.findAll().forEach(p -> existing.put(p.getItemId(), p));
        Map<Integer, Boolean> known = new HashMap<>();
        items.findAll().forEach(i -> known.put(i.getId(), Boolean.TRUE));

        List<LatestPrice> toSave = new ArrayList<>();
        for (Map.Entry<String, WikiPricesClient.LatestPoint> e : latest.entrySet()) {
            Integer id = parseId(e.getKey());
            if (id == null || !known.containsKey(id)) {
                continue;
            }
            WikiPricesClient.LatestPoint p = e.getValue();
            if (p == null) {
                continue;
            }
            LatestPrice row = existing.computeIfAbsent(id, LatestPrice::new);
            row.update(p.high(), p.low(), toInstant(p.highTime()), toInstant(p.lowTime()));
            toSave.add(row);
        }
        latestPrices.saveAll(toSave);
        return toSave.size();
    }

    /**
     * Append the most recent completed five-minute window to history, and copy
     * its volumes onto the latest-price rows.
     *
     * <p>Idempotent by design. The window's own timestamp is the key, so
     * re-running this for a window already stored writes nothing new -- a retry
     * after a partial failure cannot double-count volume into the averages.
     */
    @Transactional
    public int archiveFiveMinuteWindow() {
        WikiPricesClient.FiveMinute window = wiki.fiveMinute();
        if (window == null || window.data() == null || window.timestamp() == null) {
            log.warn("Five-minute response was empty; nothing archived");
            return 0;
        }
        Instant capturedAt = Instant.ofEpochSecond(window.timestamp());

        Map<Integer, LatestPrice> priceRows = new HashMap<>();
        latestPrices.findAll().forEach(p -> priceRows.put(p.getItemId(), p));

        List<PriceHistory> rows = new ArrayList<>();
        for (Map.Entry<String, WikiPricesClient.FiveMinutePoint> e : window.data().entrySet()) {
            Integer id = parseId(e.getKey());
            WikiPricesClient.FiveMinutePoint p = e.getValue();
            if (id == null || p == null || !priceRows.containsKey(id)) {
                continue;
            }
            long highVol = p.highPriceVolume() == null ? 0 : p.highPriceVolume();
            long lowVol = p.lowPriceVolume() == null ? 0 : p.lowPriceVolume();

            // Volumes belong on the current-price row too, so the flip finder
            // can filter on liquidity without touching the history table.
            priceRows.get(id).updateVolumes(highVol, lowVol);

            if (history.existsByItemIdAndCapturedAt(id, capturedAt)) {
                continue;
            }
            rows.add(new PriceHistory(id, p.avgHighPrice(), p.avgLowPrice(),
                    highVol, lowVol, capturedAt));
        }
        history.saveAll(rows);
        return rows.size();
    }

    private static Integer parseId(String key) {
        try {
            return Integer.valueOf(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant toInstant(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }
}
