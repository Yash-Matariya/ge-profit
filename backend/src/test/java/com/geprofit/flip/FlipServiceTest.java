package com.geprofit.flip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geprofit.config.GeProfitProperties;
import com.geprofit.repo.LatestPriceRepository;
import com.geprofit.repo.projection.PricedItem;
import com.geprofit.tax.GeTax;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The flip arithmetic and filtering, with the repository mocked.
 *
 * <p>No database and no HTTP: these tests are about whether the numbers are
 * right and whether the filters keep the correct rows, which is the part that
 * would quietly give bad trading advice if it broke.
 */
class FlipServiceTest {

    private final GeTax tax = new GeTax(new GeProfitProperties(
            new GeProfitProperties.Wiki("http://localhost", "test", 5),
            new GeProfitProperties.Ingest(false, 1, 1, 1),
            new GeProfitProperties.Tax(0.02, 5_000_000, 50),
            new GeProfitProperties.Items(561, 227)));

    private final LatestPriceRepository repo = mock(LatestPriceRepository.class);
    private final FlipService service = new FlipService(repo, tax);

    @Test
    @DisplayName("margin is sell minus tax minus buy")
    void computesMarginAfterTax() {
        given(row(1, "Test item", 900, 1000, 10, 1000, false));

        Flip flip = service.find(query(FlipQuery.Sort.MARGIN)).get(0);

        assertThat(flip.buyAt()).isEqualTo(900);
        assertThat(flip.sellAt()).isEqualTo(1000);
        assertThat(flip.tax()).isEqualTo(20);
        assertThat(flip.margin()).isEqualTo(80);
    }

    @Test
    @DisplayName("potential profit is margin times the buy limit, which is what a flip is really worth")
    void potentialProfitUsesTheBuyLimit() {
        given(row(1, "Big margin, tiny limit", 100_000, 110_000, 8, 5000, false),
              row(2, "Small margin, huge limit", 100, 160, 13_000, 5000, false));

        List<Flip> byPotential = service.find(query(FlipQuery.Sort.POTENTIAL_PROFIT));

        // Item 1: margin 110000 - 2200 - 100000 = 7800, x8 = 62,400
        // Item 2: margin 160 - 3 - 100 = 57, x13000 = 741,000
        // Ranked by raw margin item 1 wins; by what you can actually make, item 2 does.
        assertThat(byPotential.get(0).itemId()).isEqualTo(2);
        assertThat(service.find(query(FlipQuery.Sort.MARGIN)).get(0).itemId()).isEqualTo(1);
    }

    @Test
    @DisplayName("the default query ranks by potential profit, so nobody can quietly switch it back to margin")
    void defaultSortIsPotentialProfit() {
        given(row(1, "Big margin, tiny limit", 100_000, 110_000, 8, 5000, false),
              row(2, "Small margin, huge limit", 100, 160, 13_000, 5000, false));

        assertThat(FlipQuery.defaults().sort()).isEqualTo(FlipQuery.Sort.POTENTIAL_PROFIT);
        assertThat(service.find(FlipQuery.defaults()).get(0).itemId()).isEqualTo(2);
    }

    @Test
    @DisplayName("an item with no published buy limit counts as one unit, not unlimited")
    void missingBuyLimitIsTreatedAsOne() {
        given(row(1, "No limit published", 100, 200, null, 1000, false));

        Flip flip = service.find(query(FlipQuery.Sort.MARGIN)).get(0);

        assertThat(flip.buyLimit()).isEqualTo(1);
        assertThat(flip.potentialProfit()).isEqualTo(flip.margin());
    }

    @Test
    @DisplayName("illiquid items are filtered out however good the margin looks")
    void filtersByVolume() {
        given(row(1, "Never traded", 1, 1_000_000, 100, 3, false));

        List<Flip> result = service.find(new FlipQuery(
                1, 1000, 0, 0, false, false, FlipQuery.Sort.MARGIN, 100));

        assertThat(result).isEmpty();
    }

    @Test
    void filtersByMaxBuyPrice() {
        given(row(1, "Expensive", 5_000_000, 5_500_000, 10, 5000, false),
              row(2, "Cheap", 100, 200, 10, 5000, false));

        List<Flip> result = service.find(new FlipQuery(
                1, 0, 1000, 0, false, false, FlipQuery.Sort.MARGIN, 100));

        assertThat(result).extracting(Flip::itemId).containsExactly(2);
    }

    @Test
    @DisplayName("a trade that loses money after tax is excluded by the default minimum margin")
    void excludesLosingTrades() {
        // Sell 1000, buy 990, tax 20 -> margin is -10.
        given(row(1, "Loser", 990, 1000, 10, 5000, false));

        assertThat(service.find(query(FlipQuery.Sort.MARGIN))).isEmpty();
    }

    @Test
    void membersAndFreeToPlayFiltersAreExclusive() {
        given(row(1, "Members item", 100, 200, 10, 5000, true),
              row(2, "F2P item", 100, 200, 10, 5000, false));

        List<Flip> members = service.find(new FlipQuery(
                1, 0, 0, 0, true, false, FlipQuery.Sort.MARGIN, 100));
        List<Flip> f2p = service.find(new FlipQuery(
                1, 0, 0, 0, false, true, FlipQuery.Sort.MARGIN, 100));

        assertThat(members).extracting(Flip::itemId).containsExactly(1);
        assertThat(f2p).extracting(Flip::itemId).containsExactly(2);
    }

    @Test
    void respectsTheLimit() {
        given(row(1, "A", 100, 200, 10, 5000, false),
              row(2, "B", 100, 200, 10, 5000, false),
              row(3, "C", 100, 200, 10, 5000, false));

        List<Flip> result = service.find(new FlipQuery(
                1, 0, 0, 0, false, false, FlipQuery.Sort.MARGIN, 2));

        assertThat(result).hasSize(2);
    }

    // ---- helpers -----------------------------------------------------------

    private void given(PricedItem... rows) {
        when(repo.findAllPriced()).thenReturn(List.of(rows));
    }

    private static FlipQuery query(FlipQuery.Sort sort) {
        return new FlipQuery(1, 0, 0, 0, false, false, sort, 100);
    }

    private static PricedItem row(int id, String name, int buyAt, int sellAt,
                                  Integer buyLimit, long volume, boolean members) {
        PricedItem p = mock(PricedItem.class);
        when(p.getItemId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getBuyAt()).thenReturn(buyAt);
        when(p.getSellAt()).thenReturn(sellAt);
        when(p.getBuyLimit()).thenReturn(buyLimit);
        when(p.getHighVolume()).thenReturn(volume / 2);
        when(p.getLowVolume()).thenReturn(volume / 2);
        when(p.getMembers()).thenReturn(members);
        when(p.getUpdatedAt()).thenReturn(Instant.now());
        return p;
    }
}
