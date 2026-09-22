package com.geprofit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An item in the game, from the Wiki's /mapping endpoint.
 *
 * <p>The id is the game's own item id, not a surrogate key. That is deliberate:
 * it is stable, it is what every other endpoint keys on, and inventing our own
 * id would mean a lookup table for no benefit. Because the id comes from
 * outside, this entity does not use a generated identifier.
 */
@Entity
@Table(name = "items")
public class Item {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    private String examine;

    @Column(nullable = false)
    private boolean members;

    /**
     * How many of this item one account may buy per four hours. Null when the
     * Wiki does not publish a limit -- which is different from a limit of zero,
     * so it stays nullable rather than defaulting.
     */
    @Column(name = "buy_limit")
    private Integer buyLimit;

    @Column(name = "high_alch")
    private Integer highAlch;

    @Column(name = "low_alch")
    private Integer lowAlch;

    @Column(name = "shop_value")
    private Integer shopValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Item() {
        // for JPA
    }

    public Item(Integer id, String name, String examine, boolean members,
                Integer buyLimit, Integer highAlch, Integer lowAlch, Integer shopValue) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.members = members;
        this.buyLimit = buyLimit;
        this.highAlch = highAlch;
        this.lowAlch = lowAlch;
        this.shopValue = shopValue;
        this.updatedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getExamine() {
        return examine;
    }

    public boolean isMembers() {
        return members;
    }

    public Integer getBuyLimit() {
        return buyLimit;
    }

    public Integer getHighAlch() {
        return highAlch;
    }

    public Integer getLowAlch() {
        return lowAlch;
    }

    public Integer getShopValue() {
        return shopValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Refresh the mutable fields from a newer mapping fetch. */
    public void updateFrom(String name, String examine, boolean members, Integer buyLimit,
                           Integer highAlch, Integer lowAlch, Integer shopValue) {
        this.name = name;
        this.examine = examine;
        this.members = members;
        this.buyLimit = buyLimit;
        this.highAlch = highAlch;
        this.lowAlch = lowAlch;
        this.shopValue = shopValue;
        this.updatedAt = Instant.now();
    }
}
