package com.geprofit.process;

import com.geprofit.config.GeProfitProperties;
import com.geprofit.repo.projection.PricedItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Derives the recipe list from the item catalogue.
 *
 * <p>The alternative -- typing out every recipe in the game -- would be
 * thousands of lines that rot the moment Jagex adds an item. Instead each
 * family is expressed as a naming rule and matched against the catalogue, so
 * new herbs and new potions are picked up automatically.
 *
 * <p>Two families are implemented, both one-step conversions with a real
 * market on each side:
 *
 * <ul>
 *   <li><b>Herb cleaning.</b> "Grimy X" becomes "X". One input, one output.
 *   <li><b>Unfinished potions.</b> A clean herb plus a vial of water becomes
 *       "X potion (unf)". Two inputs, so it exercises the multi-input path.
 * </ul>
 */
@Component
public class RecipeBuilder {

    private static final String GRIMY_PREFIX = "Grimy ";
    private static final String UNF_SUFFIX = " potion (unf)";

    private final GeProfitProperties props;

    public RecipeBuilder(GeProfitProperties props) {
        this.props = props;
    }

    public List<Recipe> buildFrom(List<PricedItem> priced) {
        Map<String, PricedItem> byName = new HashMap<>();
        for (PricedItem p : priced) {
            byName.put(p.getName(), p);
        }

        List<Recipe> recipes = new ArrayList<>();
        recipes.addAll(herbCleaning(byName));
        recipes.addAll(unfinishedPotions(byName));
        return recipes;
    }

    /** "Grimy ranarr weed" -> "Ranarr weed". */
    private List<Recipe> herbCleaning(Map<String, PricedItem> byName) {
        List<Recipe> out = new ArrayList<>();
        for (PricedItem grimy : byName.values()) {
            if (!grimy.getName().startsWith(GRIMY_PREFIX)) {
                continue;
            }
            // "Grimy ranarr weed" -> "Ranarr weed": drop the prefix and
            // capitalise, because the clean herb is a sentence-cased name.
            String stem = grimy.getName().substring(GRIMY_PREFIX.length());
            String cleanName = capitalise(stem);
            PricedItem clean = byName.get(cleanName);
            if (clean == null) {
                continue;
            }
            out.add(new Recipe(
                    "Clean " + stem,
                    List.of(new Recipe.Ingredient(grimy.getItemId(), grimy.getName(), 1)),
                    clean.getItemId(), clean.getName(), 1,
                    "Herblore"));
        }
        return out;
    }

    /** "Ranarr weed" + "Vial of water" -> "Ranarr potion (unf)". */
    private List<Recipe> unfinishedPotions(Map<String, PricedItem> byName) {
        PricedItem vial = findById(byName, props.items().vialOfWater());
        if (vial == null) {
            return List.of();
        }

        List<Recipe> out = new ArrayList<>();
        for (PricedItem unf : byName.values()) {
            if (!unf.getName().endsWith(UNF_SUFFIX)) {
                continue;
            }
            // "Ranarr potion (unf)" -> herb stem "Ranarr". The herb itself is
            // usually "Ranarr weed", so try the stem and the common suffixes.
            String stem = unf.getName().substring(0, unf.getName().length() - UNF_SUFFIX.length());
            PricedItem herb = firstPresent(byName, stem, stem + " weed", stem + " leaf");
            if (herb == null) {
                continue;
            }
            out.add(new Recipe(
                    "Make " + unf.getName(),
                    List.of(new Recipe.Ingredient(herb.getItemId(), herb.getName(), 1),
                            new Recipe.Ingredient(vial.getItemId(), vial.getName(), 1)),
                    unf.getItemId(), unf.getName(), 1,
                    "Herblore"));
        }
        return out;
    }

    private static PricedItem firstPresent(Map<String, PricedItem> byName, String... names) {
        for (String n : names) {
            PricedItem p = byName.get(n);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private static PricedItem findById(Map<String, PricedItem> byName, int itemId) {
        return byName.values().stream()
                .filter(p -> p.getItemId() == itemId)
                .findFirst()
                .orElse(null);
    }

    private static String capitalise(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
