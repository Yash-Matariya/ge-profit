package com.geprofit.flip;

/**
 * The filters a caller can apply to the flip finder.
 *
 * <p>Kept as its own type, with the accept rule beside the fields, so the
 * filtering logic is unit-testable without a database and the controller stays
 * a thin translation from query parameters.
 */
public record FlipQuery(long minMargin,
                        long minVolume,
                        long maxBuyPrice,
                        double minRoiPercent,
                        boolean membersOnly,
                        boolean freeToPlayOnly,
                        Sort sort,
                        int limit) {

    public enum Sort {
        POTENTIAL_PROFIT, MARGIN, ROI, VOLUME
    }

    /** Sensible defaults: profitable, liquid, ranked by what it is really worth. */
    public static FlipQuery defaults() {
        return new FlipQuery(1, 100, 0, 0, false, false, Sort.POTENTIAL_PROFIT, 100);
    }

    public boolean accepts(Flip f) {
        if (f.margin() < minMargin) {
            return false;
        }
        if (f.volume() < minVolume) {
            return false;
        }
        if (maxBuyPrice > 0 && f.buyAt() > maxBuyPrice) {
            return false;
        }
        if (f.roiPercent() < minRoiPercent) {
            return false;
        }
        if (membersOnly && !f.members()) {
            return false;
        }
        if (freeToPlayOnly && f.members()) {
            return false;
        }
        return true;
    }
}
