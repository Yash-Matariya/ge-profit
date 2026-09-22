package com.geprofit.tax;

import static org.assertj.core.api.Assertions.assertThat;

import com.geprofit.config.GeProfitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The tax rule, tested hardest.
 *
 * <p>Every profit number in the application is derived from this class, so an
 * error here is an error everywhere and it would not look like a bug -- it
 * would look like slightly wrong advice, which is worse.
 */
class GeTaxTest {

    private static final double RATE = 0.02;
    private static final long CAP = 5_000_000;
    private static final int EXEMPT_BELOW = 50;

    private final GeTax tax = new GeTax(new GeProfitProperties(
            new GeProfitProperties.Wiki("http://localhost", "test", 5),
            new GeProfitProperties.Ingest(false, 1, 1, 1),
            new GeProfitProperties.Tax(RATE, CAP, EXEMPT_BELOW),
            new GeProfitProperties.Items(561, 227)));

    @ParameterizedTest(name = "selling at {0} costs {1} tax")
    @CsvSource({
            "0, 0",
            "1, 0",
            "49, 0",        // just under the exemption
            "50, 1",        // the first taxed price: floor(50 * 0.02) == 1
            "100, 2",
            "1000, 20",
            "1000000, 20000",
    })
    void chargesThePercentageAboveTheExemption(long sellPrice, long expectedTax) {
        assertThat(tax.on(sellPrice)).isEqualTo(expectedTax);
    }

    @Test
    @DisplayName("cheap items are exempt, so a 49gp sale is tax free")
    void exemptBelowThreshold() {
        assertThat(tax.on(EXEMPT_BELOW - 1)).isZero();
        assertThat(tax.on(EXEMPT_BELOW)).isPositive();
    }

    @Test
    @DisplayName("tax rounds down, never up")
    void roundsDown() {
        // floor(99 * 0.02) == floor(1.98) == 1, not 2.
        assertThat(tax.on(99)).isEqualTo(1);
        // floor(1499 * 0.02) == floor(29.98) == 29.
        assertThat(tax.on(1499)).isEqualTo(29);
    }

    @Test
    @DisplayName("tax is capped, so very expensive items are not taxed proportionally")
    void capped() {
        long hugePrice = 10_000_000_000L;
        assertThat(tax.on(hugePrice)).isEqualTo(CAP);
    }

    @Test
    void netSellIsPriceMinusTax() {
        assertThat(tax.netSell(1000)).isEqualTo(1000 - 20);
    }

    @Test
    @DisplayName("margin subtracts tax from the sell side only")
    void marginTakesTaxOffTheSale() {
        // Buy at 900, sell at 1000. Tax is 20, so the margin is 80 -- not 100.
        assertThat(tax.margin(900, 1000)).isEqualTo(80);
    }

    @Test
    @DisplayName("a losing trade reports a negative margin rather than zero")
    void marginCanBeNegative() {
        // The naive implementation clamps at zero and hides real losses.
        assertThat(tax.margin(1000, 900)).isNegative();
    }
}
