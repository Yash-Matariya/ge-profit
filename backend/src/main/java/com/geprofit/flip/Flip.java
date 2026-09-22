package com.geprofit.flip;

import java.time.Instant;

/**
 * One flip opportunity: buy at {@code buyAt}, sell at {@code sellAt}, keep
 * {@code margin} per unit after tax.
 *
 * @param potentialProfit margin multiplied by the buy limit -- what the trade is
 *                        actually worth over one four-hour window, and the
 *                        number to rank by
 */
public record Flip(int itemId,
                   String name,
                   long buyAt,
                   long sellAt,
                   long tax,
                   long margin,
                   double roiPercent,
                   int buyLimit,
                   long potentialProfit,
                   long volume,
                   boolean members,
                   Instant pricedAt) {
}
