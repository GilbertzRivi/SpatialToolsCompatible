package net.oktawia.spatialtoolscmp.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;
import net.oktawia.spatialtoolscmp.items.helpers.SpatialMultiTool;

public class SpatialItemModelProvider extends ItemModelProvider {
    public SpatialItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, SpatialToolsCMP.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        ResourceLocation multiToolModel = new ResourceLocation(
                SpatialToolsCMP.MODID,
                "item/" + ForgeRegistries.ITEMS.getKey(SpatialItemRegistrar.PORTABLE_SPATIAL_TOOL.get()).getPath());

        for (var item : SpatialItemRegistrar.getItems()) {
            ItemModelBuilder builder = simpleItem(item);

            if (SpatialMultiTool.getMode(new ItemStack(item)) == null) {
                continue;
            }

            builder.override()
                    .predicate(SpatialToolsCMP.makeId(SpatialToolsCMP.MULTI_TOOL_MODEL_PROPERTY), 1.0F)
                    .model(new ModelFile.UncheckedModelFile(multiToolModel))
                    .end();
        }
    }

    private ItemModelBuilder simpleItem(Item item) {
        return withExistingParent(ForgeRegistries.ITEMS.getKey(item).getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                        new ResourceLocation(SpatialToolsCMP.MODID,
                                "item/" + ForgeRegistries.ITEMS.getKey(item).getPath()));
    }
}
