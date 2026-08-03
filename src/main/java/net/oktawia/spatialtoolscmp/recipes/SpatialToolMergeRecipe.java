package net.oktawia.spatialtoolscmp.recipes;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;
import net.oktawia.spatialtoolscmp.items.helpers.SpatialMultiTool;

public class SpatialToolMergeRecipe extends ShapelessRecipe {

    public SpatialToolMergeRecipe(ResourceLocation id) {
        super(
                id,
                "",
                CraftingBookCategory.MISC,
                new ItemStack(SpatialItemRegistrar.PORTABLE_SPATIAL_TOOL.get()),
                ingredients());
    }

    private static NonNullList<Ingredient> ingredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();

        for (SpatialMultiTool.Mode mode : SpatialMultiTool.MODES) {
            ingredients.add(Ingredient.of(mode.item()));
        }

        return ingredients;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return SpatialMultiTool.merge(containerTools(container)).tool();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        List<ItemStack> leftovers = SpatialMultiTool.merge(containerTools(container)).leftovers();

        for (int i = 0; i < leftovers.size() && i < remaining.size(); i++) {
            remaining.set(i, leftovers.get(i));
        }

        return remaining;
    }

    private static List<ItemStack> containerTools(CraftingContainer container) {
        List<ItemStack> tools = new ArrayList<>();

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);

            if (SpatialMultiTool.getMode(stack) != null) {
                tools.add(stack);
            }
        }

        return tools;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SpatialRecipeSerializerRegistrar.SPATIAL_TOOL_MERGE.get();
    }

    public static class Serializer implements RecipeSerializer<SpatialToolMergeRecipe> {

        @Override
        public SpatialToolMergeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new SpatialToolMergeRecipe(id);
        }

        @Override
        public SpatialToolMergeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new SpatialToolMergeRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SpatialToolMergeRecipe recipe) {
        }
    }
}
