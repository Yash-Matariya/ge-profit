package com.geprofit.decant;

import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.projection.PricedItem;
import com.geprofit.tax.GeTax;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Finds potions whose dose forms are mispriced against each other.
 *
 * <p>Potions exist as (1), (2), (3) and (4) dose versions of the same drink,
 * and a decanter converts freely between them <em>preserving total doses</em>.
 * Four 1-dose potions become one 4-dose potion. So the forms should all cost
 * the same per dose -- and they routinely do not, because players buy the
 * convenient 4-dose and dump the awkward leftovers.
 *
 * <p><b>Working in doses is what makes this correct.</b> The tempting version
 * is to compare potion prices directly, which is wrong because the forms are
 * not the same quantity of anything. Reduce every form to a price per dose and
 * the comparison becomes valid:
 *
 * <pre>
 *   cost per dose    = buy price  / doses
 *   revenue per dose = (sell price - tax) / doses
 * </pre>
 *
 * <p>The best trade in a family is the cheapest form to buy against the most
 * valuable form to sell, and the profit is the gap between them multiplied by
 * however many doses you move.
 */
@Service
public class DecantService {

    /** "Prayer potion(4)" -> base "Prayer potion", dose 4. */
    private static final Pattern DOSE = Pattern.compile("^(.*)\\(([1-4])\\)$");

    /** A trade below this many doses a day is not worth listing. */
    private static final long MIN_DAILY_DOSE_VOLUME = 100;

    private final LatestPriceRepository prices;
    private final GeTax tax;

    public DecantService(LatestPriceRepository prices, GeTax tax) {
        this.prices = prices;
        this.tax = tax;
    }

    public List<DecantOpportunity> find(long minProfitPerDose, int limit) {
        Map<String, List<Form>> families = groupIntoFamilies(prices.findAllPriced());

        List<DecantOpportunity> out = new ArrayList<>();
        for (Map.Entry<String, List<Form>> e : families.entrySet()) {
            // A family needs at least two forms for there to be a trade at all.
            if (e.getValue().size() < 2) {
                continue;
            }
            DecantOpportunity best = bestTradeIn(e.getKey(), e.getValue());
            if (best != null && best.profitPerDose() >= minProfitPerDose) {
                out.add(best);
            }
        }
        out.sort(Comparator.comparingLong(DecantOpportunity::profitPerHundredDoses).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private Map<String, List<Form>> groupIntoFamilies(List<PricedItem> rows) {
        Map<String, List<Form>> families = new HashMap<>();
        for (PricedItem row : rows) {
            Matcher m = DOSE.matcher(row.getName());
            if (!m.matches()) {
                continue;
            }
            String base = m.group(1).trim();
            int doses = Integer.parseInt(m.group(2));
            long volume = (row.getHighVolume() == null ? 0 : row.getHighVolume())
                    + (row.getLowVolume() == null ? 0 : row.getLowVolume());

            families.computeIfAbsent(base, k -> new ArrayList<>())
                    .add(new Form(row.getItemId(), row.getName(), doses,
                            row.getBuyAt(), row.getSellAt(), volume));
        }
        return families;
    }

    /**
     * The best buy-form / sell-form pair in one potion family.
     *
     * <p>Buying and selling the same form is excluded: that is not a decant,
     * it is an ordinary flip, and the flip finder already covers it.
     */
    private DecantOpportunity bestTradeIn(String base, List<Form> forms) {
        Form bestBuy = null;
        Form bestSell = null;
        double bestGap = 0;

        for (Form buy : forms) {
            for (Form sell : forms) {
                if (buy.itemId() == sell.itemId()) {
                    continue;
                }
                // Liquidity has to hold on both legs -- a trade you can enter
                // but not exit is worse than no trade.
                if (buy.volume() < MIN_DAILY_DOSE_VOLUME || sell.volume() < MIN_DAILY_DOSE_VOLUME) {
                    continue;
                }
                double gap = revenuePerDose(sell) - costPerDose(buy);
                if (gap > bestGap) {
                    bestGap = gap;
                    bestBuy = buy;
                    bestSell = sell;
                }
            }
        }

        if (bestBuy == null) {
            return null;
        }

        long profitPerDose = (long) Math.floor(bestGap);
        // 100 doses is a readable unit: 25 four-dose potions, or roughly one
        // inventory of them.
        long per100 = (long) Math.floor(bestGap * 100);

        return new DecantOpportunity(
                base,
                bestBuy.itemId(), bestBuy.name(), bestBuy.doses(), bestBuy.buyAt(),
                bestSell.itemId(), bestSell.name(), bestSell.doses(), bestSell.sellAt(),
                tax.on(bestSell.sellAt()),
                round2(costPerDose(bestBuy)),
                round2(revenuePerDose(bestSell)),
                profitPerDose,
                per100,
                Math.min(bestBuy.volume(), bestSell.volume()));
    }

    private double costPerDose(Form f) {
        return (double) f.buyAt() / f.doses();
    }

    private double revenuePerDose(Form f) {
        return (double) tax.netSell(f.sellAt()) / f.doses();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** One dose form of one potion, with its current two-sided price. */
    private record Form(int itemId, String name, int doses, long buyAt, long sellAt, long volume) {
    }
}
