package com.geprofit.process;

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
 * The high-alchemy and recipe calculators, with the repository mocked.
 *
 * <p>Both share one rule worth pinning down: inputs are bought at the ask,
 * outputs are sold net of tax, and alching pays no tax because it sells to the
 * game rather than to a player.
 */
class ProcessingServiceTest {

    private static final int NATURE_RUNE = 561;
    private static final int VIAL_OF_WATER = 227;

    private final GeProfitProperties props = new GeProfitProperties(
            new GeProfitProperties.Wiki("http://localhost", "test", 5),
            new GeProfitProperties.Ingest(false, 1, 1, 1),
            new GeProfitProperties.Tax(0.02, 5_000_000, 50),
            new GeProfitProperties.Items(NATURE_RUNE, VIAL_OF_WATER));

    private final LatestPriceRepository repo = mock(LatestPriceRepository.class);
    private final ProcessingService service =
            new ProcessingService(repo, new RecipeBuilder(props), new GeTax(props), props);

    @Test
    @DisplayName("alch profit is the alch value minus the item and one nature rune, with no tax")
    void alchProfitHasNoTax() {
        given(row(NATURE_RUNE, "Nature rune", 100, 110, null, 1000),
              row(1, "Rune platebody", 38_000, 39_000, 39_000, 1000));

        AlchOpportunity a = service.highAlch(1, 0, 10).get(0);

        // 39,000 alch - (38,000 item + 100 rune) = 900. A 2% tax would make it 120.
        assertThat(a.itemId()).isEqualTo(1);
        assertThat(a.natureRuneCost()).isEqualTo(100);
        assertThat(a.profitPerItem()).isEqualTo(900);
    }

    @Test
    @DisplayName("without a nature rune price, alching returns nothing rather than wrong numbers")
    void noRunePriceMeansNoAlchResults() {
        given(row(1, "Rune platebody", 38_000, 39_000, 39_000, 1000));

        assertThat(service.highAlch(1, 0, 10)).isEmpty();
    }

    @Test
    @DisplayName("cleaning a herb buys the grimy one and sells the clean one after tax")
    void herbCleaningMargin() {
        given(row(10, "Grimy ranarr weed", 6_000, 6_100, null, 1000),
              row(11, "Ranarr weed", 6_400, 6_500, null, 1000));

        RecipeMargin r = service.recipes(1, 0, 10).get(0);

        // 6,500 sell - 130 tax - 6,000 buy = 370
        assertThat(r.recipe()).isEqualTo("Clean ranarr weed");
        assertThat(r.inputCost()).isEqualTo(6_000);
        assertThat(r.revenueAfterTax()).isEqualTo(6_370);
        assertThat(r.profit()).isEqualTo(370);
    }

    @Test
    @DisplayName("an unfinished potion pays for both inputs, the herb and the vial")
    void unfinishedPotionCostsBothInputs() {
        given(row(VIAL_OF_WATER, "Vial of water", 5, 6, null, 1000),
              row(11, "Ranarr weed", 6_400, 6_500, null, 1000),
              row(12, "Ranarr potion (unf)", 6_800, 7_000, null, 1000));

        RecipeMargin r = service.recipes(1, 0, 10).get(0);

        // 7,000 sell - 140 tax - (6,400 herb + 5 vial) = 455
        assertThat(r.recipe()).isEqualTo("Make Ranarr potion (unf)");
        assertThat(r.inputs()).hasSize(2);
        assertThat(r.inputCost()).isEqualTo(6_405);
        assertThat(r.profit()).isEqualTo(455);
    }

    @Test
    @DisplayName("a recipe with an unpriced input is skipped, because unknown is not free")
    void unpricedInputSkipsTheRecipe() {
        // No vial of water price, so the unfinished potion can't be valued.
        given(row(11, "Ranarr weed", 6_400, 6_500, null, 1000),
              row(12, "Ranarr potion (unf)", 6_800, 7_000, null, 1000));

        assertThat(service.recipes(1, 0, 10)).isEmpty();
    }

    // ---- helpers -----------------------------------------------------------

    private void given(PricedItem... rows) {
        when(repo.findAllPriced()).thenReturn(List.of(rows));
    }

    private static PricedItem row(int id, String name, int buyAt, int sellAt,
                                  Integer highAlch, long volume) {
        PricedItem p = mock(PricedItem.class);
        when(p.getItemId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.getBuyAt()).thenReturn(buyAt);
        when(p.getSellAt()).thenReturn(sellAt);
        when(p.getHighAlch()).thenReturn(highAlch);
        when(p.getBuyLimit()).thenReturn(100);
        when(p.getHighVolume()).thenReturn(volume / 2);
        when(p.getLowVolume()).thenReturn(volume / 2);
        when(p.getMembers()).thenReturn(true);
        when(p.getUpdatedAt()).thenReturn(Instant.now());
        return p;
    }
}
