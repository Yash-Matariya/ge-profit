package com.geprofit;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Shows Grand Exchange flips in a side panel, ranked by what you can actually
 * make rather than by raw margin.
 *
 * <p>Two things this plugin is careful about, both RuneLite requirements and
 * both worth understanding rather than copying:
 *
 * <ul>
 *   <li><b>Network access is opt-in.</b> Fetching prices contacts a third-party
 *       server, so it is off by default and the config item carries the
 *       IP-address warning. Nothing reaches the network until the player turns
 *       it on.
 *   <li><b>Nothing blocks the client thread.</b> Requests go out via OkHttp's
 *       {@code enqueue()}, and every update to the panel is pushed back onto
 *       the Swing thread. Doing either the other way round freezes the game.
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "GE Profit",
	description = "Grand Exchange flips ranked by profit after tax and buy limits",
	tags = {"grand", "exchange", "ge", "flip", "money", "merch", "prices"}
)
public class GeProfitPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private GeProfitConfig config;

	@Inject
	private GePricesService pricesService;

	@Inject
	private GeProfitBackendService backendService;

	@Inject
	private ScheduledExecutorService executor;

	private GeProfitPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refreshTask;

	@Provides
	GeProfitConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GeProfitConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new GeProfitPanel(this::refresh);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GE Profit")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		scheduleRefresh();
	}

	@Override
	protected void shutDown()
	{
		// Cancel before removing the panel: a refresh completing after shutdown
		// would update a panel that is no longer on screen.
		cancelRefresh();
		clientToolbar.removeNavigation(navButton);
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GeProfitConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		// The refresh interval or the data source may have changed, so rebuild
		// the schedule rather than leaving the old one running.
		scheduleRefresh();
	}

	private void scheduleRefresh()
	{
		cancelRefresh();

		if (!config.fetchPrices() && !config.useBackend())
		{
			// Nothing is enabled, so nothing should touch the network. Say so
			// in the panel instead of silently showing an empty list.
			onError("Turn on \"Fetch live prices\" in the plugin settings to load flips.");
			return;
		}

		int seconds = Math.max(15, config.refreshSeconds());
		refreshTask = executor.scheduleWithFixedDelay(
			this::refresh, 0, seconds, TimeUnit.SECONDS);
	}

	private void cancelRefresh()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
	}

	/**
	 * Load flips from whichever source is enabled.
	 *
	 * <p>The backend wins when both are on: it applies the same rules as the
	 * website and knows about price history, so its answers are the better
	 * ones.
	 */
	private void refresh()
	{
		if (panel == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() -> panel.setLoading(true));

		if (config.useBackend())
		{
			backendService.fetchFlips(config.backendUrl(), config.minMargin(),
				config.minHourlyVolume(), config.maxBuyPrice(), this::onFlips, this::onError);
		}
		else if (config.fetchPrices())
		{
			pricesService.findFlips(config.minMargin(), config.minHourlyVolume(),
				config.maxBuyPrice(), this::onFlips, this::onError);
		}
	}

	/** Called on an OkHttp thread, so hop to the Swing thread before touching the UI. */
	private void onFlips(List<FlipItem> flips)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setFlips(flips);
			}
		});
	}

	private void onError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setError(message);
			}
		});
	}
}
