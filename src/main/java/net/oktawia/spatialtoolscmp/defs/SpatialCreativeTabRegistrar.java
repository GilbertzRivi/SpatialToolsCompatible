package net.oktawia.spatialtoolscmp.defs;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SpatialCreativeTabRegistrar {

    private static final List<Supplier<? extends Item>> extraItems = new ArrayList<>();

    public static void registerExtraItem(Supplier<? extends Item> item) {
        extraItems.add(item);
    }

    public static final ResourceLocation ID = SpatialToolsCMP.makeId("tab");

    public static final CreativeModeTab TAB = CreativeModeTab.builder()
            .title(Component.translatable(LangDefs.MOD_NAME.getTranslationKey()))
            .icon(() -> new ItemStack(SpatialItemRegistrar.PORTABLE_SPATIAL_CLONER.get()))
            .displayItems(SpatialCreativeTabRegistrar::populate)
            .build();

    private static void populate(CreativeModeTab.ItemDisplayParameters ignored, CreativeModeTab.Output out) {
        SpatialItemRegistrar.ITEMS.getEntries().forEach(ro -> out.accept(ro.get()));
        extraItems.forEach(s -> out.accept(new ItemStack(s.get())));
    }

    private SpatialCreativeTabRegistrar() {}
}
