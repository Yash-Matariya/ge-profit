package com.geprofit.process;

import java.util.List;

/** What a recipe is worth right now: inputs bought at the ask, output sold net of tax. */
public record RecipeMargin(String recipe,
                           String skill,
                           List<Recipe.Ingredient> inputs,
                           int outputItemId,
                           String outputName,
                           int outputQuantity,
                           long inputCost,
                           long revenueAfterTax,
                           long profit,
                           double roiPercent,
                           long outputVolume) {
}
