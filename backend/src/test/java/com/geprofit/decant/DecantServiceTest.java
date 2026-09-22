package com.geprofit.decant;

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
 * The dose arithmetic.
 *
 * <p>The thing being protected here is that comparisons happen <em>per dose</em>.
 * A test that only checked "profit is positive" would pass for an implementation
 * that compared potion prices directly, which is the exact bug this service
 * exists to avoid.
 */
class DecantServiceTest {

    private final GeTax tax = new GeTax(new GeProfitProperties(
            new GeProfitProperties.Wiki("http://localhost", "test", 5),
            new GeProfitProperties.Ingest(false, 1, 1, 1),
            new GeProfitProperties.Tax(0.02, 5_000_000, 50),
            new GeProfitProperties.Items(561, 227)));

    private final LatestPriceRepository repo = mock(LatestPriceRepository.class);
    private final DecantService service = new DecantService(repo, tax);

    @Test
    @DisplayName("finds the gap when the 4-dose is dearer per dose than the 3-dose")
    void findsPerDoseGap() {
        // (3) costs 300 to buy -> 100 per dose.
        // (4) sells for 1000 -> tax 20 -> 980 net -> 245 per dose.
        given(potion(1, "Prayer potion(3)", 3, 300, 310, 5000),
              potion(2, "Prayer potion(4)", 4, 990, 1000, 5000));

        List<DecantOpportunity> found = service.find(1, 10);

        assertThat(found).hasSize(1);
        DecantOpportunity o = found.get(0);
        assertThat(o.potion()).isEqualTo("Prayer potion");
        assertThat(o.buyDoses()).isEqualTo(3);
        assertThat(o.sellDoses()).isEqualTo(4);
        assertThat(o.costPerDose()).isEqualTo(100.0);
        assertThat(o.revenuePerDose()).isEqualTo(245.0);
        assertThat(o.profitPerDose()).isEqualTo(145);
        assertThat(o.profitPerHundredDoses()).isEqualTo(14_500);
    }

    @Test
    @DisplayName("a family priced consistently per dose yields nothing")
    void noOpportunityWhenPricedFairly() {
        // Both forms are 100 per dose before tax; tax makes selling a small loss,
        // so there is no trade here at all.
        given(potion(1, "Super attack(3)", 3, 300, 300, 5000),
              potion(2, "Super attack(4)", 4, 400, 400, 5000));

        assertThat(service.find(1, 10)).isEmpty();
    }

    @Test
    @DisplayName("a potion with only one dose form is not a decant opportunity")
    void ignoresSingleFormFamilies() {
        given(potion(1, "Weird brew(1)", 1, 10, 10_000, 5000));

        assertThat(service.find(1, 10)).isEmpty();
    }

    @Test
    @DisplayName("illiquid forms are skipped, because a trade you cannot exit is not a trade")
    void requiresVolumeOnBothLegs() {
        given(potion(1, "Rare potion(3)", 3, 300, 310, 5000),
              potion(2, "Rare potion(4)", 4, 990, 5000, 2));   // sells twice a day

        assertThat(service.find(1, 10)).isEmpty();
    }

    @Test
    @DisplayName("items that are not dosed potions are ignored")
    void ignoresNonPotions() {
        given(potionNamed(1, "Dragon scimitar", 100_000, 110_000, 5000),
              potionNamed(2, "Rune platebody", 38_000, 39_000, 5000));

        assertThat(service.find(1, 10)).isEmpty();
    }

    @Test
    @DisplayName("tax is taken off the sale, so it reduces revenue per dose")
    void taxReducesRevenuePerDose() {
        // (4) sells at 1000 -> 20 tax -> 980 / 4 = 245 per dose, not 250.
        given(potion(1, "Test potion(1)", 1, 1, 2, 5000),
              potion(2, "Test potion(4)", 4, 990, 1000, 5000));

        DecantOpportunity o = service.find(1, 10).get(0);

        assertThat(o.revenuePerDose()).isEqualTo(245.0);
        assertThat(o.taxPerUnitSold()).isEqualTo(20);
    }

    // ---- helpers -----------------------------------------------------------

    private void given(PricedItem... rows) {
        when(repo.findAllPriced()).thenReturn(List.of(rows));
    }

    private static PricedItem potion(int id, String name, int doses,
                                     int buyAt, int sellAt, long volume) {
        // doses is encoded in the name; the parameter documents the intent.
        assert name.endsWith("(" + doses + ")");
        return potionNamed(id, name, buyAt, sellAt, volume);
    }

    private static PricedItem potionNamed(int id, String name, int buyAt, int sellAt, long volume) {
        PricedItem p = mock(PricedItem.class);
        when(p.getItemId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getBuyAt()).thenReturn(buyAt);
        when(p.getSellAt()).thenReturn(sellAt);
        when(p.getBuyLimit()).thenReturn(1000);
        when(p.getHighVolume()).thenReturn(volume / 2);
        when(p.getLowVolume()).thenReturn(volume / 2);
        when(p.getMembers()).thenReturn(true);
        when(p.getUpdatedAt()).thenReturn(Instant.now());
        return p;
    }
}
