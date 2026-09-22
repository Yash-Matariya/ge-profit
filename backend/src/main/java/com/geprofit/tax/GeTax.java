package com.geprofit.tax;

import com.geprofit.config.GeProfitProperties;
import org.springframework.stereotype.Component;

/**
 * The Grand Exchange sales tax.
 *
 * <p>Every profit number in this application is wrong if this class is wrong,
 * so it lives on its own and is the only place the rule is expressed. The rate,
 * the cap and the exemption threshold are configuration rather than constants,
 * because all three have changed during OSRS's lifetime -- when Jagex changes
 * them again this is a config edit, not a code change.
 *
 * <p>The rule: the seller pays a percentage of the sale price, rounded
 * <em>down</em>, capped per item, and items below a threshold price are exempt.
 */
@Component
public class GeTax {

    private final double rate;
    private final long cap;
    private final int exemptBelow;

    public GeTax(GeProfitProperties props) {
        this.rate = props.tax().rate();
        this.cap = props.tax().cap();
        this.exemptBelow = props.tax().exemptBelow();
    }

    /**
     * Tax paid when selling one unit at {@code sellPrice}.
     *
     * @return the tax in coins, never negative
     */
    public long on(long sellPrice) {
        if (sellPrice < exemptBelow) {
            return 0;
        }
        // Floor, not round: the game rounds the tax down in the player's favour.
        long tax = (long) Math.floor(sellPrice * rate);
        return Math.min(tax, cap);
    }

    /** What actually lands in your pocket after selling one unit. */
    public long netSell(long sellPrice) {
        return sellPrice - on(sellPrice);
    }

    /**
     * Profit on buying one unit at {@code buyPrice} and selling it at
     * {@code sellPrice}. Can be negative -- that is a real answer, not an error.
     */
    public long margin(long buyPrice, long sellPrice) {
        return netSell(sellPrice) - buyPrice;
    }
}
