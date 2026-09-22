package com.geprofit;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Talks to the OSRS Wiki real-time prices API (prices.runescape.wiki) and turns
 * it into a ranked list of flips.
 *
 * All requests go on OkHttp's thread pool via enqueue() — never on the client
 * thread. The item mapping (id -> name, buy limit) is static-ish, so it's fetched
 * once and cached; /latest and /1h are fetched each refresh.
 */
@Slf4j
@Singleton
public class GePricesService
{
	private static final HttpUrl BASE =
		HttpUrl.get("https://prices.runescape.wiki/api/v1/osrs");
	// The Wiki asks for a descriptive User-Agent. Add contact info before publishing.
	private static final String USER_AGENT = "ge-profit RuneLite plugin";

	// Grand Exchange sale tax. NOTE: verify against the live game — the rate,
	// cap and exemption have changed over OSRS's history. Kept here as constants
	// so there's one place to correct.
	private static final double TAX_RATE = 0.02;
	private static final int TAX_CAP = 5_000_000;
	private static final int TAX_EXEMPT_BELOW = 50;

	private final OkHttpClient httpClient;
	private final Gson gson;

	// itemId -> (name, buy limit), cached after the first mapping fetch
	private final Map<Integer, MappingItem> mapping = new HashMap<>();

	@Inject
	GePricesService(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/** Tax paid when selling one unit at {@code sellPrice}. */
	static int taxFor(int sellPrice)
	{
		if (sellPrice < TAX_EXEMPT_BELOW)
		{
			return 0;
		}
		return (int) Math.min(TAX_CAP, Math.floor(sellPrice * TAX_RATE));
	}

	/**
	 * Refresh prices and hand back the flips that pass the given filters, best
	 * margin first. Runs off-thread; the callback fires on an OkHttp thread, so
	 * the caller must marshal any UI work onto the Swing thread.
	 */
	public void findFlips(int minMargin, int minHourlyVolume, int maxBuyPrice,
						  Consumer<List<FlipItem>> onResult, Consumer<String> onError)
	{
		if (mapping.isEmpty())
		{
			fetchMapping(() -> fetchAndCompute(minMargin, minHourlyVolume, maxBuyPrice, onResult, onError), onError);
		}
		else
		{
			fetchAndCompute(minMargin, minHourlyVolume, maxBuyPrice, onResult, onError);
		}
	}

	private void fetchAndCompute(int minMargin, int minHourlyVolume, int maxBuyPrice,
								 Consumer<List<FlipItem>> onResult, Consumer<String> onError)
	{
		get("latest", body ->
		{
			Map<String, PricePoint> latest = gson.fromJson(body, LatestResponse.class).data;
			get("1h", volBody ->
			{
				Map<String, VolPoint> vol = gson.fromJson(volBody, VolResponse.class).data;
				onResult.accept(compute(latest, vol, minMargin, minHourlyVolume, maxBuyPrice));
			}, onError);
		}, onError);
	}

	private List<FlipItem> compute(Map<String, PricePoint> latest, Map<String, VolPoint> vol,
								   int minMargin, int minHourlyVolume, int maxBuyPrice)
	{
		List<FlipItem> flips = new ArrayList<>();
		for (Map.Entry<String, PricePoint> e : latest.entrySet())
		{
			PricePoint p = e.getValue();
			if (p == null || p.high == null || p.low == null || p.high <= p.low)
			{
				continue;
			}
			int buy = p.low, sell = p.high;
			if (maxBuyPrice > 0 && buy > maxBuyPrice)
			{
				continue;
			}
			int tax = taxFor(sell);
			int margin = sell - buy - tax;
			if (margin < minMargin)
			{
				continue;
			}
			int id = Integer.parseInt(e.getKey());
			VolPoint v = vol.get(e.getKey());
			long hourly = v == null ? 0 : v.total();
			if (hourly < minHourlyVolume)
			{
				continue;
			}
			MappingItem m = mapping.get(id);
			String name = m != null ? m.name : "Item " + id;
			int limit = m != null && m.limit != null ? m.limit : 0;
			flips.add(new FlipItem(id, name, buy, sell, tax, margin, limit, hourly));
		}
		flips.sort(Comparator.comparingInt(FlipItem::getMargin).reversed());
		return flips;
	}

	private void fetchMapping(Runnable onDone, Consumer<String> onError)
	{
		get("mapping", body ->
		{
			Type listType = new TypeToken<List<MappingItem>>() {}.getType();
			List<MappingItem> items = gson.fromJson(body, listType);
			for (MappingItem m : items)
			{
				mapping.put(m.id, m);
			}
			log.debug("Loaded {} item mappings", mapping.size());
			onDone.run();
		}, onError);
	}

	private void get(String path, Consumer<String> onBody, Consumer<String> onError)
	{
		Request request = new Request.Builder()
			.url(BASE.newBuilder().addPathSegment(path).build())
			.header("User-Agent", USER_AGENT)
			.build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException ex)
			{
				log.debug("GE prices request failed: {}", path, ex);
				onError.accept("Couldn't reach the price service. Retrying next refresh.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						onError.accept("Price service returned " + r.code() + ".");
						return;
					}
					onBody.accept(r.body().string());
				}
				catch (Exception ex)
				{
					log.debug("Failed to parse GE prices: {}", path, ex);
					onError.accept("Couldn't read the price data.");
				}
			}
		});
	}

	// ---- Wiki API response shapes (Gson) ----

	private static class LatestResponse
	{
		Map<String, PricePoint> data;
	}

	private static class PricePoint
	{
		Integer high;
		Integer low;
	}

	private static class VolResponse
	{
		Map<String, VolPoint> data;
	}

	private static class VolPoint
	{
		@SerializedName("highPriceVolume")
		Long highVolume;
		@SerializedName("lowPriceVolume")
		Long lowVolume;

		long total()
		{
			return (highVolume == null ? 0 : highVolume) + (lowVolume == null ? 0 : lowVolume);
		}
	}

	private static class MappingItem
	{
		int id;
		String name;
		Integer limit;
	}
}
