package net.oktawia.spatialtoolscmp.defs;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.items.PortableSpatialStorage;
import net.oktawia.spatialtoolscmp.items.PortableSpatialTool;

import java.util.List;

public class SpatialItemRegistrar {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SpatialToolsCMP.MODID);

    public static List<Item> getItems() {
        return ITEMS.getEntries()
                .stream()
                .map(RegistryObject::get)
                .toList();
    }

    public static final RegistryObject<PortableSpatialStorage> PORTABLE_SPATIAL_STORAGE =
            ITEMS.register("portable_spatial_storage",
                    () -> new PortableSpatialStorage(new Item.Properties()));

    public static final RegistryObject<PortableSpatialCloner> PORTABLE_SPATIAL_CLONER =
            ITEMS.register("portable_spatial_cloner",
                    () -> new PortableSpatialCloner(new Item.Properties()));

    public static final RegistryObject<PortableSpatialReplacer> PORTABLE_SPATIAL_REPLACER =
            ITEMS.register("portable_spatial_replacer",
                    () -> new PortableSpatialReplacer(new Item.Properties()));

    public static final RegistryObject<PortableSpatialPiper> PORTABLE_SPATIAL_PIPER =
            ITEMS.register("portable_spatial_piper",
                    () -> new PortableSpatialPiper(new Item.Properties()));

    public static final RegistryObject<PortableSpatialTool> PORTABLE_SPATIAL_TOOL =
            ITEMS.register("portable_spatial_tool",
                    () -> new PortableSpatialTool(new Item.Properties()));

    private SpatialItemRegistrar() {}
}