package com.geprofit.process;

/**
 * An item worth alching.
 *
 * <p>Note that no tax appears here: High Level Alchemy sells to the game rather
 * than to another player, so the Grand Exchange tax does not apply to the alch
 * value. Only the purchase side touches the market.
 */
public record AlchOpportunity(int itemId,
                              String name,
                              long buyAt,
                              long highAlchValue,
                              long natureRuneCost,
                              long profitPerItem,
                              long profitPerHour,
                              long volume,
                              int buyLimit) {
}
