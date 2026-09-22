package com.geprofit.repo.projection;

import java.time.Instant;

/**
 * An item joined to its current price, loaded in a single query.
 *
 * <p>A Spring Data interface projection: Spring implements it from the aliases
 * in the JPQL, so the query selects only these columns instead of hydrating
 * entities. That matters here because the flip finder reads every priced item
 * in the game on each request.
 */
public interface PricedItem {

    Integer getItemId();

    String getName();

    Integer getBuyLimit();

    Integer getHighAlch();

    Boolean getMembers();

    /** The price you can buy at (the API's "low"). */
    Integer getBuyAt();

    /** The price you can sell at (the API's "high"). */
    Integer getSellAt();

    Long getHighVolume();

    Long getLowVolume();

    Instant getUpdatedAt();
}
