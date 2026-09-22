package com.geprofit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The current price of one item. Overwritten on every poll -- this table is
 * "now", and {@link PriceHistory} is "before".
 *
 * <p><b>The naming trap worth knowing:</b> the API's {@code high} is the price
 * of the most recent <em>buy</em> from the perspective of the other player,
 * which is the price <em>you sell at</em>; {@code low} is what you <em>buy
 * at</em>. Getting these backwards inverts every margin in the application, so
 * the accessors are named {@link #sellAt()} and {@link #buyAt()} and the rest
 * of the code uses those instead of high and low.
 */
@Entity
@Table(name = "latest_prices")
public class LatestPrice {

    @Id
    @Column(name = "item_id")
    private Integer itemId;

    private Integer high;

    private Integer low;

    @Column(name = "high_time")
    private Instant highTime;

    @Column(name = "low_time")
    private Instant lowTime;

    @Column(name = "high_volume")
    private Long highVolume;

    @Column(name = "low_volume")
    private Long lowVolume;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LatestPrice() {
        // for JPA
    }

    public LatestPrice(Integer itemId) {
        this.itemId = itemId;
    }

    public void update(Integer high, Integer low, Instant highTime, Instant lowTime) {
        this.high = high;
        this.low = low;
        this.highTime = highTime;
        this.lowTime = lowTime;
        this.updatedAt = Instant.now();
    }

    public void updateVolumes(Long highVolume, Long lowVolume) {
        this.highVolume = highVolume;
        this.lowVolume = lowVolume;
    }

    public Integer getItemId() {
        return itemId;
    }

    /** The price you can SELL at right now (the API calls it "high"). */
    public Integer sellAt() {
        return high;
    }

    /** The price you can BUY at right now (the API calls it "low"). */
    public Integer buyAt() {
        return low;
    }

    public Integer getHigh() {
        return high;
    }

    public Integer getLow() {
        return low;
    }

    public Instant getHighTime() {
        return highTime;
    }

    public Instant getLowTime() {
        return lowTime;
    }

    public Long getHighVolume() {
        return highVolume;
    }

    public Long getLowVolume() {
        return lowVolume;
    }

    public long totalVolume() {
        return (highVolume == null ? 0 : highVolume) + (lowVolume == null ? 0 : lowVolume);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Both sides quoted, so a margin can actually be computed. */
    public boolean isTradeable() {
        return high != null && low != null && high > 0 && low > 0;
    }
}
