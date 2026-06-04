package net.oktawia.spatialtoolscmp.defs;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialReplacerMenu;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialStorageMenu;

public class SpatialMenuRegistrar {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SpatialToolsCMP.MODID);

    private static <T extends AbstractContainerMenu> RegistryObject<MenuType<T>> register(
            String id,
            MenuType.MenuSupplier<T> factory
    ) {
        return MENU_TYPES.register(
                id,
                () -> new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS)
        );
    }

    public static final RegistryObject<MenuType<PortableSpatialStorageMenu>> PORTABLE_SPATIAL_STORAGE_MENU =
            register("portable_spatial_storage_menu", PortableSpatialStorageMenu::new);

    public static final RegistryObject<MenuType<PortableSpatialClonerMenu>> PORTABLE_SPATIAL_CLONER_MENU =
            register("portable_spatial_cloner_menu", PortableSpatialClonerMenu::new);

    public static final RegistryObject<MenuType<PortableSpatialReplacerMenu>> PORTABLE_SPATIAL_REPLACER_MENU =
            register("portable_spatial_replacer_menu", PortableSpatialReplacerMenu::new);

    private SpatialMenuRegistrar() {
    }
}