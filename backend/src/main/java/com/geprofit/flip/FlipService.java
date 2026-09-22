package com.geprofit.flip;

import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.projection.PricedItem;
import com.geprofit.tax.GeTax;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Finds items worth buying and reselling.
 *
 * <p>The arithmetic is the easy half. The half that makes the numbers
 * <em>useful</em> is the filtering, because the naive version of this tool --
 * sort every item by raw margin -- produces a list of things you can never
 * actually trade:
 *
 * <ul>
 *   <li><b>The buy limit caps the real prize.</b> A 2,000gp margin on an item
 *       you may buy 8 of per four hours is worth 16,000gp, less than a 50gp
 *       margin on something with a limit of 13,000. Ranking by margin alone
 *       is the single most common mistake in flip finders, so the default sort
 *       here is <em>potential profit</em>, which is margin multiplied by the
 *       limit.
 *   <li><b>Volume decides whether the trade fills.</b> A huge margin on an item
 *       that trades twice a day is a price gap nobody is crossing, not an
 *       opportunity.
 *   <li><b>Tax comes off the sell side</b>, and on expensive items it is the
 *       difference between profit and loss.
 * </ul>
 */
@Service
public class FlipService {

    private final LatestPriceRepository prices;
    private final GeTax tax;

    public FlipService(LatestPriceRepository prices, GeTax tax) {
        this.prices = prices;
        this.tax = tax;
    }

    public List<Flip> find(FlipQuery query) {
        return prices.findAllPriced().stream()
                .map(this::toFlip)
                .filter(f -> query.accepts(f))
                .sorted(comparatorFor(query.sort()))
                .limit(query.limit())
                .toList();
    }

    private Flip toFlip(PricedItem row) {
        long buyAt = row.getBuyAt();
        long sellAt = row.getSellAt();
        long taxPaid = tax.on(sellAt);
        long margin = sellAt - taxPaid - buyAt;

        // The limit is per account per four hours. Where the Wiki publishes no
        // limit we treat one unit as the honest assumption rather than pretend
        // the opportunity is unbounded.
        int limit = row.getBuyLimit() == null || row.getBuyLimit() <= 0 ? 1 : row.getBuyLimit();
        long potential = margin * limit;

        double roi = buyAt == 0 ? 0 : (double) margin / buyAt * 100.0;
        long volume = (row.getHighVolume() == null ? 0 : row.getHighVolume())
                + (row.getLowVolume() == null ? 0 : row.getLowVolume());

        return new Flip(row.getItemId(), row.getName(), buyAt, sellAt, taxPaid, margin,
                roi, limit, potential, volume,
                Boolean.TRUE.equals(row.getMembers()), row.getUpdatedAt());
    }

    private static Comparator<Flip> comparatorFor(FlipQuery.Sort sort) {
        return switch (sort) {
            case MARGIN -> Comparator.comparingLong(Flip::margin).reversed();
            case ROI -> Comparator.comparingDouble(Flip::roiPercent).reversed();
            case VOLUME -> Comparator.comparingLong(Flip::volume).reversed();
            case POTENTIAL_PROFIT -> Comparator.comparingLong(Flip::potentialProfit).reversed();
        };
    }
}
