package com.geprofit.process;

import com.geprofit.config.GeProfitProperties;
import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.projection.PricedItem;
import com.geprofit.tax.GeTax;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Turning one item into a more valuable one: high alchemy and crafting recipes.
 *
 * <p>Both calculators share one rule that is easy to get wrong: <b>you buy at
 * the ask and sell at the bid, and tax comes off the sale.</b> Valuing inputs
 * at what they sell for, or outputs at what they cost, produces profits that
 * do not exist. Every number below buys inputs at {@code buyAt} and sells
 * outputs at {@code netSell(sellAt)}.
 */
@Service
public class ProcessingService {

    private final LatestPriceRepository prices;
    private final RecipeBuilder recipeBuilder;
    private final GeTax tax;
    private final GeProfitProperties props;

    public ProcessingService(LatestPriceRepository prices,
                             RecipeBuilder recipeBuilder,
                             GeTax tax,
                             GeProfitProperties props) {
        this.prices = prices;
        this.recipeBuilder = recipeBuilder;
        this.tax = tax;
        this.props = props;
    }

    /**
     * Items worth casting High Level Alchemy on.
     *
     * <p>Alchemy sells to the game, not to another player, so <b>no GE tax is
     * paid on the alch value</b> -- a detail that flatters alching against
     * flipping and is easy to miss. The cost side is the item plus one nature
     * rune, both bought at the ask.
     */
    public List<AlchOpportunity> highAlch(long minProfit, long minVolume, int limit) {
        List<PricedItem> all = prices.findAllPriced();
        Long naturePrice = priceOf(all, props.items().natureRune());
        if (naturePrice == null) {
            // Without a rune price every number would be wrong by a constant.
            // Returning nothing is better than returning something misleading.
            return List.of();
        }

        List<AlchOpportunity> out = new ArrayList<>();
        for (PricedItem row : all) {
            Integer alch = row.getHighAlch();
            if (alch == null || alch <= 0) {
                continue;
            }
            long volume = volumeOf(row);
            if (volume < minVolume) {
                continue;
            }
            long cost = row.getBuyAt() + naturePrice;
            long profit = alch - cost;
            if (profit < minProfit) {
                continue;
            }
            int perHour = 1200;  // roughly one cast every three seconds
            out.add(new AlchOpportunity(row.getItemId(), row.getName(), row.getBuyAt(),
                    alch, naturePrice, profit, profit * perHour, volume,
                    row.getBuyLimit() == null ? 0 : row.getBuyLimit()));
        }
        out.sort(Comparator.comparingLong(AlchOpportunity::profitPerItem).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /**
     * Recipes whose output sells for more than its inputs cost.
     *
     * <p>A recipe whose inputs are not all currently priced is skipped rather
     * than valued at zero: a missing price is unknown, not free, and treating
     * it as free would put a fake opportunity at the top of the list.
     */
    public List<RecipeMargin> recipes(long minProfit, long minVolume, int limit) {
        List<PricedItem> all = prices.findAllPriced();
        Map<Integer, PricedItem> byId = new HashMap<>();
        for (PricedItem p : all) {
            byId.put(p.getItemId(), p);
        }

        List<RecipeMargin> out = new ArrayList<>();
        for (Recipe recipe : recipeBuilder.buildFrom(all)) {
            PricedItem output = byId.get(recipe.outputItemId());
            if (output == null) {
                continue;
            }

            long inputCost = 0;
            boolean priced = true;
            for (Recipe.Ingredient in : recipe.inputs()) {
                PricedItem p = byId.get(in.itemId());
                if (p == null) {
                    priced = false;
                    break;
                }
                inputCost += (long) p.getBuyAt() * in.quantity();
            }
            if (!priced) {
                continue;
            }

            long revenue = tax.netSell(output.getSellAt()) * recipe.outputQuantity();
            long profit = revenue - inputCost;
            if (profit < minProfit) {
                continue;
            }

            long volume = volumeOf(output);
            if (volume < minVolume) {
                continue;
            }

            double roi = inputCost == 0 ? 0 : (double) profit / inputCost * 100.0;
            out.add(new RecipeMargin(recipe.name(), recipe.skill(), recipe.inputs(),
                    recipe.outputItemId(), recipe.outputName(), recipe.outputQuantity(),
                    inputCost, revenue, profit, round2(roi), volume));
        }
        out.sort(Comparator.comparingLong(RecipeMargin::profit).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private static Long priceOf(List<PricedItem> all, int itemId) {
        return all.stream()
                .filter(p -> p.getItemId() == itemId)
                .map(p -> (long) p.getBuyAt())
                .findFirst()
                .orElse(null);
    }

    private static long volumeOf(PricedItem row) {
        return (row.getHighVolume() == null ? 0 : row.getHighVolume())
                + (row.getLowVolume() == null ? 0 : row.getLowVolume());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
