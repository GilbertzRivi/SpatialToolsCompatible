package net.oktawia.spatialtoolscmp.xei.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PowerUpgradePanelWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SpatialOffsetControlsWidget;
import net.oktawia.spatialtoolscmp.client.screens.AbstractPortableStructureToolScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialClonerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialStorageScreen;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public final class SpatialJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = SpatialToolsCMP.makeId("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(
                PortableSpatialStorageScreen.class,
                new StructureToolGuiHandler<>()
        );

        registration.addGuiContainerHandler(
                PortableSpatialClonerScreen.class,
                new StructureToolGuiHandler<>()
        );
    }

    private static final class StructureToolGuiHandler<T extends AbstractPortableStructureToolScreen<?>>
            implements IGuiContainerHandler<T> {

        @Override
        public List<Rect2i> getGuiExtraAreas(T screen) {
            List<Rect2i> areas = new ArrayList<>();

            areas.add(SpatialOffsetControlsWidget.getExtraArea(screen));

            int upgradeSlots = screen.getMenu().getPowerUpgradeSlotCount();
            if (upgradeSlots > 0) {
                areas.add(PowerUpgradePanelWidget.getExtraArea(screen, upgradeSlots));
            }

            return areas;
        }
    }
}