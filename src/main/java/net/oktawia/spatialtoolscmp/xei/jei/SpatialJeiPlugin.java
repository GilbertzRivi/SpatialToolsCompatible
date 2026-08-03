package net.oktawia.spatialtoolscmp.xei.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PowerUpgradePanelWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SpatialOffsetControlsWidget;
import net.oktawia.spatialtoolscmp.client.screens.AbstractPortableStructureToolScreen;
import net.oktawia.spatialtoolscmp.client.screens.AbstractSpatialToolScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialClonerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialPiperScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialReplacerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialStorageScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialToolScreen;

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
                new StructureToolGuiHandler<>());

        registration.addGuiContainerHandler(
                PortableSpatialClonerScreen.class,
                new StructureToolGuiHandler<>());

        registration.addGuiContainerHandler(
                PortableSpatialReplacerScreen.class,
                new UpgradePanelGuiHandler<>());

        registration.addGuiContainerHandler(
                PortableSpatialPiperScreen.class,
                new UpgradePanelGuiHandler<>());

        registration.addGuiContainerHandler(
                PortableSpatialToolScreen.class,
                new UpgradePanelGuiHandler<>());
    }

    private static List<Rect2i> commonExtraAreas(AbstractSpatialToolScreen<?> screen) {
        List<Rect2i> areas = new ArrayList<>();

        int upgradeSlots = screen.getMenu().getPowerUpgradeSlotCount();

        if (upgradeSlots > 0) {
            areas.add(PowerUpgradePanelWidget.getExtraArea(screen, upgradeSlots));
        }

        if (screen.hasToolModeDropdown()) {
            areas.add(screen.getToolModeDropdownArea());
        }

        return areas;
    }

    private static final class UpgradePanelGuiHandler<T extends AbstractSpatialToolScreen<?>>
            implements IGuiContainerHandler<T> {

        @Override
        public List<Rect2i> getGuiExtraAreas(T screen) {
            return commonExtraAreas(screen);
        }
    }

    private static final class StructureToolGuiHandler<T extends AbstractPortableStructureToolScreen<?>>
            implements IGuiContainerHandler<T> {

        @Override
        public List<Rect2i> getGuiExtraAreas(T screen) {
            List<Rect2i> areas = commonExtraAreas(screen);

            areas.add(SpatialOffsetControlsWidget.getExtraArea(screen));

            return areas;
        }
    }
}
