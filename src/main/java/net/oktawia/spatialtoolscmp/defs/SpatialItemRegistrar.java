package net.oktawia.spatialtoolscmp.defs;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialStorage;

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

    private SpatialItemRegistrar() {}
}