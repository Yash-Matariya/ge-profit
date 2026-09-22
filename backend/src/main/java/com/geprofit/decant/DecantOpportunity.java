package com.geprofit.decant;

/**
 * Buy {@code buyName}, decant it, sell it as {@code sellName}.
 *
 * <p>Everything is reported per dose as well as per unit, because the dose is
 * the only quantity the two forms share -- see {@link DecantService} for why
 * comparing potion prices directly is the wrong calculation.
 */
public record DecantOpportunity(String potion,
                                int buyItemId,
                                String buyName,
                                int buyDoses,
                                long buyAt,
                                int sellItemId,
                                String sellName,
                                int sellDoses,
                                long sellAt,
                                long taxPerUnitSold,
                                double costPerDose,
                                double revenuePerDose,
                                long profitPerDose,
                                long profitPerHundredDoses,
                                long limitingVolume) {
}
