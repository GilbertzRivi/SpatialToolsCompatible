package net.oktawia.spatialtoolscmp.defs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

public final class SpatialCreativeTabRegistrar {

    public static final ResourceLocation ID = SpatialToolsCMP.makeId("tab");

    public static final CreativeModeTab TAB = CreativeModeTab.builder()
            .title(Component.translatable(LangDefs.MOD_NAME.getTranslationKey()))
            .icon(() -> new ItemStack(SpatialItemRegistrar.PORTABLE_SPATIAL_CLONER.get()))
            .displayItems(SpatialCreativeTabRegistrar::populate)
            .build();

    private static void populate(CreativeModeTab.ItemDisplayParameters ignored, CreativeModeTab.Output out) {
        SpatialItemRegistrar.ITEMS.getEntries().forEach(ro -> out.accept(ro.get()));
    }

    private SpatialCreativeTabRegistrar() {}
}
