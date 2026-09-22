package com.geprofit.ingest;

import com.geprofit.config.GeProfitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link PriceIngestionService} on a schedule.
 *
 * <p><b>Why this is a separate class</b>, rather than {@code @Scheduled}
 * methods on the service itself: Spring implements {@code @Transactional} with
 * a proxy, and a call from one method of a bean to another method of the
 * <em>same</em> bean does not go through that proxy. So a scheduled method
 * calling its own transactional method runs with no transaction at all —
 * entities loaded inside it are detached the moment their repository call
 * returns, and changes to them are silently discarded.
 *
 * <p>That is not hypothetical. It is exactly the bug that made this project's
 * volume figures stay at zero while every other part of ingestion appeared to
 * work: rows were written, so it looked healthy, but the in-place updates
 * vanished. Splitting the scheduler from the work makes every call cross the
 * proxy, so the transactions are real.
 *
 * <p>Each job logs and swallows its own failure. A missed poll is a gap in the
 * data; an exception escaping a scheduled method silently stops that job for
 * the lifetime of the process, which is far worse.
 */
@Component
public class PriceIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceIngestionScheduler.class);

    private final PriceIngestionService ingestion;
    private final GeProfitProperties props;

    public PriceIngestionScheduler(PriceIngestionService ingestion, GeProfitProperties props) {
        this.ingestion = ingestion;
        this.props = props;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${geprofit.ingest.mapping-interval-ms}")
    public void mapping() {
        if (disabled()) {
            return;
        }
        try {
            log.info("Catalogue refreshed: {} items", ingestion.refreshCatalogue());
        } catch (Exception e) {
            log.warn("Catalogue refresh failed; keeping the previous catalogue", e);
        }
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${geprofit.ingest.latest-interval-ms}")
    public void latest() {
        if (disabled()) {
            return;
        }
        try {
            log.debug("Latest prices refreshed: {} items", ingestion.refreshLatestPrices());
        } catch (Exception e) {
            log.warn("Latest price refresh failed; serving the previous prices", e);
        }
    }

    @Scheduled(initialDelay = 15_000, fixedDelayString = "${geprofit.ingest.history-interval-ms}")
    public void history() {
        if (disabled()) {
            return;
        }
        try {
            log.debug("Archived {} history rows", ingestion.archiveFiveMinuteWindow());
        } catch (Exception e) {
            log.warn("History archive failed; this window is a gap", e);
        }
    }

    /**
     * Turning ingestion off lets a second instance run as a read-only replica
     * without two pollers hammering the Wiki.
     */
    private boolean disabled() {
        return !props.ingest().enabled();
    }
}
