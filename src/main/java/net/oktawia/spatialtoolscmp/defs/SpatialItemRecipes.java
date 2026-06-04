package net.oktawia.spatialtoolscmp.defs;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SpatialItemRecipes {

    public record RecipeDef(
            String id,
            @Nullable String pattern,
            Map<Character, Item> keys,
            List<Item> shapelessIngredients,
            Item output,
            int count
    ) {}

    private static final List<RecipeDef> RECIPES = new ArrayList<>();

    public static List<RecipeDef> getRecipes() {
        return Collections.unmodifiableList(RECIPES);
    }

    public static Builder recipe(String id) {
        return new Builder(id);
    }

    public static void registerRecipes() {
        recipe("portable_spatial_storage")
                .shaped(" B /DLD/CSC")
                .define('L', Blocks.REDSTONE_LAMP)
                .define('D', Items.DIAMOND)
                .define('S', Items.NETHER_STAR)
                .define('B', Items.STONE_BUTTON)
                .define('C', Blocks.CHEST)
                .output(SpatialItemRegistrar.PORTABLE_SPATIAL_STORAGE.get())
                .register();
        recipe("portable_spatial_cloner")
                .shaped(" L /DSD/EEE")
                .define('L', Blocks.REDSTONE_LAMP)
                .define('D', Items.DIAMOND)
                .define('S', SpatialItemRegistrar.PORTABLE_SPATIAL_STORAGE.get())
                .define('E', Items.ENDER_PEARL)
                .output(SpatialItemRegistrar.PORTABLE_SPATIAL_CLONER.get())
                .register();
        recipe("portable_spatial_replacer")
                .shaped(" L /DSD/EEE")
                .define('L', Blocks.REDSTONE_LAMP)
                .define('D', Items.DIAMOND)
                .define('S', SpatialItemRegistrar.PORTABLE_SPATIAL_STORAGE.get())
                .define('E', Items.ENDER_EYE)
                .output(SpatialItemRegistrar.PORTABLE_SPATIAL_REPLACER.get())
                .register();
    }

    public static class Builder {
        private final String id;
        private String pattern = null;
        private final Map<Character, Item> keys = new LinkedHashMap<>();
        private final List<Item> shapelessIngredients = new ArrayList<>();
        private Item output = null;
        private int count = 1;

        private Builder(String id) {
            this.id = id;
        }

        public Builder shaped(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder define(char key, ItemLike item) {
            keys.put(key, item.asItem());
            return this;
        }

        public Builder shapeless(ItemLike... ingredients) {
            for (var i : ingredients) shapelessIngredients.add(i.asItem());
            return this;
        }

        public Builder output(ItemLike item) {
            this.output = item.asItem();
            return this;
        }

        public Builder output(ItemLike item, int count) {
            this.output = item.asItem();
            this.count = count;
            return this;
        }

        public void register() {
            RECIPES.add(new RecipeDef(id, pattern, Map.copyOf(keys), List.copyOf(shapelessIngredients), output, count));
        }
    }
}
