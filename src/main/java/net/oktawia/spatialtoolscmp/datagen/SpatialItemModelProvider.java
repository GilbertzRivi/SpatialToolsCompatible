package net.oktawia.spatialtoolscmp.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;

public class SpatialItemModelProvider extends ItemModelProvider {
    public SpatialItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, SpatialToolsCMP.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        for (var item : SpatialItemRegistrar.getItems()){
            simpleItem(item);
        }
    }

    private ItemModelBuilder simpleItem(Item item){
        return withExistingParent(ForgeRegistries.ITEMS.getKey(item).getPath(),
            new ResourceLocation("item/generated")).texture("layer0",
            new ResourceLocation(SpatialToolsCMP.MODID, "item/" + ForgeRegistries.ITEMS.getKey(item).getPath()));
    }
}
