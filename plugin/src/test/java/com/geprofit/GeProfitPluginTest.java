package com.geprofit;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a RuneLite client with this plugin loaded, for development.
 *
 * <p>Run it with {@code ./gradlew run}. It lives in the test source set because
 * it depends on the RuneLite client, which is {@code compileOnly} for the
 * plugin itself -- the real client provides it at runtime.
 */
public class GeProfitPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GeProfitPlugin.class);
		RuneLite.main(args);
	}
}
