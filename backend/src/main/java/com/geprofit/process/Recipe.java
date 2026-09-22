package com.geprofit.process;

import java.util.List;

/**
 * A conversion: consume {@code inputs}, produce {@code outputQuantity} of
 * {@code outputItemId}.
 *
 * <p>Recipes are matched by <em>name pattern</em> rather than by a hard-coded
 * list of item ids, because families like herb cleaning have dozens of members
 * that all behave identically. "Grimy ranarr weed becomes Ranarr weed" is one
 * rule covering every herb in the game, and it keeps working when a new herb is
 * added.
 *
 * @param inputs  what is consumed, as (item id, quantity) pairs
 * @param skill   what the conversion requires, shown to the user so they can
 *                tell whether they can actually do it
 */
public record Recipe(String name,
                     List<Ingredient> inputs,
                     int outputItemId,
                     String outputName,
                     int outputQuantity,
                     String skill) {

    public record Ingredient(int itemId, String name, int quantity) {
    }
}
