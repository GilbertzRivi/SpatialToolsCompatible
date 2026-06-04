package net.oktawia.spatialtoolscmp.defs;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialClonerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialReplacerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialStorageScreen;

@OnlyIn(Dist.CLIENT)
public final class SpatialScreenRegistrar {

    public static void register() {
        MenuScreens.register(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_STORAGE_MENU.get(),
                PortableSpatialStorageScreen::new
        );

        MenuScreens.register(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_CLONER_MENU.get(),
                PortableSpatialClonerScreen::new
        );

        MenuScreens.register(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_REPLACER_MENU.get(),
                PortableSpatialReplacerScreen::new
        );
    }

    private SpatialScreenRegistrar() {
    }
}