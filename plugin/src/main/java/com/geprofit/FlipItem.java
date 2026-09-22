package com.geprofit;

import lombok.Value;

/**
 * One flippable item, with everything the panel needs to show a flip.
 *
 * Convention (OSRS Wiki prices API): "low" is the most recent instant-sell price
 * (what you can buy at) and "high" is the most recent instant-buy price (what you
 * can sell at). So a flip is: buy near {@code buyPrice} (low), sell near
 * {@code sellPrice} (high). Margin already has the Grand Exchange tax removed.
 */
@Value
public class FlipItem
{
	int itemId;
	String name;
	int buyPrice;      // wiki "low"  — you buy around here
	int sellPrice;     // wiki "high" — you sell around here
	int tax;           // GE tax paid on the sale
	int margin;        // sellPrice - buyPrice - tax
	int buyLimit;      // 4-hour GE buy limit for this item
	long hourlyVolume; // units traded in the last hour (liquidity)

	/** Profit if you buy and flip a full buy-limit's worth. */
	public long marginPerLimit()
	{
		return (long) margin * buyLimit;
	}

	/** Return on investment as a percentage of the buy price. */
	public double roiPercent()
	{
		return buyPrice <= 0 ? 0 : (100.0 * margin) / buyPrice;
	}
}
