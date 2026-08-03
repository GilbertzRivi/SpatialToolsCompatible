package net.oktawia.spatialtoolscmp.recipes;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

public final class SpatialRecipeSerializerRegistrar {

    public static final String SPATIAL_TOOL_MERGE_ID = "spatial_tool_merge";

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
            .create(ForgeRegistries.RECIPE_SERIALIZERS, SpatialToolsCMP.MODID);

    public static final RegistryObject<SpatialToolMergeRecipe.Serializer> SPATIAL_TOOL_MERGE = RECIPE_SERIALIZERS
            .register(SPATIAL_TOOL_MERGE_ID, SpatialToolMergeRecipe.Serializer::new);

    private SpatialRecipeSerializerRegistrar() {
    }
}
