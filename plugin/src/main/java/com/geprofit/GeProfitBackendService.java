package com.geprofit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
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
 * Reads flips from the GE Profit backend instead of computing them in the client.
 *
 * <p><b>Why a backend at all,</b> when {@link GePricesService} already talks to
 * the Wiki directly:
 *
 * <ul>
 *   <li>The margin rules -- tax, buy limits, volume filtering, ranking by
 *       potential profit rather than raw margin -- live in one place and are
 *       unit-tested there, instead of being duplicated between the plugin and
 *       the website and drifting apart.
 *   <li>The backend keeps price history, so it can answer "is this cheap
 *       compared with the last month?". The Wiki API only serves recent
 *       windows, and a game client is the wrong place to accumulate weeks of
 *       data.
 *   <li>The Wiki sees one poller rather than one per player.
 * </ul>
 *
 * <p>The direct-to-Wiki path is kept as the default precisely because this one
 * sends the player's IP to a server <em>we</em> run. That is a bigger ask than
 * contacting the Wiki, so it is a separate opt-in with its own warning.
 *
 * <p>Every request goes out through {@code enqueue()} on OkHttp's own thread
 * pool. Blocking the client thread for network I/O would freeze the game.
 */
@Slf4j
@Singleton
public class GeProfitBackendService
{
	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	GeProfitBackendService(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Ask the backend for ranked flips.
	 *
	 * <p>The callback fires on an OkHttp thread, so anything touching Swing must
	 * be marshalled onto the EDT by the caller.
	 */
	public void fetchFlips(String baseUrl, int minMargin, int minVolume, int maxBuyPrice,
						   Consumer<List<FlipItem>> onResult, Consumer<String> onError)
	{
		HttpUrl parsed = HttpUrl.parse(baseUrl);
		if (parsed == null)
		{
			onError.accept("That backend URL isn't valid.");
			return;
		}

		HttpUrl url = parsed.newBuilder()
			.addPathSegments("api/flips")
			.addQueryParameter("minMargin", String.valueOf(minMargin))
			.addQueryParameter("minVolume", String.valueOf(minVolume))
			.addQueryParameter("maxBuyPrice", String.valueOf(maxBuyPrice))
			.addQueryParameter("sort", "POTENTIAL_PROFIT")
			.addQueryParameter("limit", "100")
			.build();

		Request request = new Request.Builder().url(url).build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException ex)
			{
				log.debug("Backend request failed", ex);
				onError.accept("Couldn't reach the GE Profit backend.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						onError.accept("Backend returned " + r.code() + ".");
						return;
					}
					Type listType = new TypeToken<List<BackendFlip>>() {}.getType();
					List<BackendFlip> flips = gson.fromJson(r.body().string(), listType);
					onResult.accept(toFlipItems(flips));
				}
				catch (Exception ex)
				{
					log.debug("Couldn't read the backend response", ex);
					onError.accept("Couldn't read the backend response.");
				}
			}
		});
	}

	private static List<FlipItem> toFlipItems(List<BackendFlip> flips)
	{
		return flips.stream()
			.map(f -> new FlipItem(f.itemId, f.name, (int) f.buyAt, (int) f.sellAt,
				(int) f.tax, (int) f.margin, f.buyLimit, f.volume))
			.collect(java.util.stream.Collectors.toList());
	}

	/**
	 * The backend's response shape.
	 *
	 * <p>Deliberately a separate type from {@link FlipItem}: the panel's model
	 * should not change shape every time the API adds a field, and mapping
	 * between them is the one place a rename would need fixing.
	 */
	private static class BackendFlip
	{
		int itemId;
		String name;
		long buyAt;
		long sellAt;
		long tax;
		long margin;
		int buyLimit;
		long volume;
	}
}
