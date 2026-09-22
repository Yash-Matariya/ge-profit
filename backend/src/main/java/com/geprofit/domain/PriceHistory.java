package com.geprofit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One five-minute price sample, appended and never updated.
 *
 * <p>This table exists because the Wiki API only serves recent windows. The
 * 1-day, 1-week and 4-week averages the dashboard shows are computed from
 * samples this application kept itself, which is the whole reason the backend
 * has a database rather than proxying the API.
 *
 * <p>A unique index on (item_id, captured_at) makes ingestion idempotent: a
 * retried poll for a window already stored conflicts instead of double-counting
 * into the averages.
 */
@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    @Column(name = "avg_high")
    private Integer avgHigh;

    @Column(name = "avg_low")
    private Integer avgLow;

    @Column(name = "high_volume", nullable = false)
    private long highVolume;

    @Column(name = "low_volume", nullable = false)
    private long lowVolume;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected PriceHistory() {
        // for JPA
    }

    public PriceHistory(Integer itemId, Integer avgHigh, Integer avgLow,
                        long highVolume, long lowVolume, Instant capturedAt) {
        this.itemId = itemId;
        this.avgHigh = avgHigh;
        this.avgLow = avgLow;
        this.highVolume = highVolume;
        this.lowVolume = lowVolume;
        this.capturedAt = capturedAt;
    }

    public Long getId() {
        return id;
    }

    public Integer getItemId() {
        return itemId;
    }

    public Integer getAvgHigh() {
        return avgHigh;
    }

    public Integer getAvgLow() {
        return avgLow;
    }

    public long getHighVolume() {
        return highVolume;
    }

    public long getLowVolume() {
        return lowVolume;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
