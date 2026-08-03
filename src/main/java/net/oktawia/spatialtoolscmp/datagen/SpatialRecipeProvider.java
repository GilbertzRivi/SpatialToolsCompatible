package net.oktawia.spatialtoolscmp.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRecipes;
import net.oktawia.spatialtoolscmp.recipes.SpatialRecipeSerializerRegistrar;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SpatialRecipeProvider extends RecipeProvider implements IConditionBuilder {

    public SpatialRecipeProvider(PackOutput pOutput) {
        super(pOutput);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        SpatialItemRecipes.registerRecipes();

        for (var recipe : SpatialItemRecipes.getRecipes()) {
            save(writer, recipe.id(), recipe.pattern(), recipe.keys(), recipe.shapelessIngredients(), recipe.output(), recipe.count());
        }

        SpecialRecipeBuilder
                .special(SpatialRecipeSerializerRegistrar.SPATIAL_TOOL_MERGE.get())
                .save(writer, SpatialToolsCMP.makeId(
                        SpatialRecipeSerializerRegistrar.SPATIAL_TOOL_MERGE_ID
                ).toString());
    }

    private void save(Consumer<FinishedRecipe> writer, String id, String pattern,
                      Map<Character, Item> keys, List<Item> shapeless, Item output, int count) {
        var unlock = has(Blocks.CRAFTING_TABLE.asItem());
        var unlockName = getHasName(Blocks.CRAFTING_TABLE.asItem());
        var recipeId = SpatialToolsCMP.makeId(id);

        if (pattern == null) {
            var builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, output, count);
            shapeless.forEach(builder::requires);
            builder.unlockedBy(unlockName, unlock);
            builder.save(writer, recipeId);
        } else {
            var builder = ShapedRecipeBuilder.shaped(RecipeCategory.MISC, output, count);
            for (var row : pattern.split("/")) builder.pattern(row);
            keys.forEach(builder::define);
            builder.unlockedBy(unlockName, unlock);
            builder.save(writer, recipeId);
        }
    }
}
