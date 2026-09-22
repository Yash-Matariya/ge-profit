package com.geprofit;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Settings for GE Profit. The config group name is specific ("geprofit") so it
 * never collides with another plugin's saved settings.
 */
@ConfigGroup(GeProfitConfig.GROUP)
public interface GeProfitConfig extends Config
{
	String GROUP = "geprofit";

	// Fetching prices calls the OSRS Wiki, a third-party server. Per RuneLite
	// rules this must be opt-in and carry the IP-address warning.
	@ConfigItem(
		keyName = "fetchPrices",
		name = "Fetch live prices",
		description = "Download live Grand Exchange prices from the OSRS Wiki to find flips.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean fetchPrices()
	{
		return false;
	}

	@ConfigItem(
		keyName = "minMargin",
		name = "Min margin (gp)",
		description = "Only show flips whose profit per item, after tax, is at least this."
	)
	default int minMargin()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "minHourlyVolume",
		name = "Min hourly volume",
		description = "Only show items that traded at least this many units in the last hour, so a flip actually fills."
	)
	default int minHourlyVolume()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "maxBuyPrice",
		name = "Max buy price (gp)",
		description = "Hide items that cost more than this to buy, to fit your budget. 0 = no limit."
	)
	default int maxBuyPrice()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "refreshSeconds",
		name = "Refresh every (s)",
		description = "How often to refresh prices. The OSRS Wiki updates roughly once a minute."
	)
	default int refreshSeconds()
	{
		return 60;
	}

	// Using the backend sends your IP to a server run by this plugin's author,
	// not to the Wiki. That is a bigger ask than the Wiki itself, so it is its
	// own opt-in with its own warning rather than being folded into the setting
	// above.
	@ConfigItem(
		keyName = "useBackend",
		name = "Use the GE Profit backend",
		description = "Fetch ranked flips from a GE Profit server instead of computing them in the client. "
			+ "The server applies the same tax and buy-limit rules as the website and keeps price history.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean useBackend()
	{
		return false;
	}

	@ConfigItem(
		keyName = "backendUrl",
		name = "Backend URL",
		description = "Where your GE Profit backend is running. Only used when the option above is enabled."
	)
	default String backendUrl()
	{
		return "http://localhost:8080";
	}
}
