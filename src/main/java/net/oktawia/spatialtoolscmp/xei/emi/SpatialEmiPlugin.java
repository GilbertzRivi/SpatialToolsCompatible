package net.oktawia.spatialtoolscmp.xei.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.renderer.Rect2i;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PowerUpgradePanelWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SpatialOffsetControlsWidget;
import net.oktawia.spatialtoolscmp.client.screens.AbstractPortableStructureToolScreen;
import net.oktawia.spatialtoolscmp.client.screens.AbstractSpatialToolScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialClonerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialPiperScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialReplacerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialStorageScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialToolScreen;

import java.util.function.Consumer;

@EmiEntrypoint
public final class SpatialEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(
                PortableSpatialStorageScreen.class,
                SpatialEmiPlugin::addStructureToolExclusions
        );

        registry.addExclusionArea(
                PortableSpatialClonerScreen.class,
                SpatialEmiPlugin::addStructureToolExclusions
        );

        registry.addExclusionArea(
                PortableSpatialReplacerScreen.class,
                SpatialEmiPlugin::addUpgradePanelExclusion
        );

        registry.addExclusionArea(
                PortableSpatialPiperScreen.class,
                SpatialEmiPlugin::addUpgradePanelExclusion
        );

        registry.addExclusionArea(
                PortableSpatialToolScreen.class,
                SpatialEmiPlugin::addUpgradePanelExclusion
        );
    }

    private static void addUpgradePanelExclusion(
            AbstractSpatialToolScreen<?> screen,
            Consumer<Bounds> consumer
    ) {
        if (screen.hasToolModeDropdown()) {
            Rect2i dropdown = screen.getToolModeDropdownArea();

            consumer.accept(new Bounds(
                    dropdown.getX(),
                    dropdown.getY(),
                    dropdown.getWidth(),
                    dropdown.getHeight()
            ));
        }

        int upgradeSlots = screen.getMenu().getPowerUpgradeSlotCount();

        if (upgradeSlots <= 0) {
            return;
        }

        consumer.accept(new Bounds(
                screen.getGuiLeft() + PowerUpgradePanelWidget.PANEL_LEFT,
                screen.getGuiTop() + PowerUpgradePanelWidget.PANEL_TOP,
                PowerUpgradePanelWidget.PANEL_WIDTH,
                PowerUpgradePanelWidget.getPanelHeight(upgradeSlots)
        ));
    }

    private static void addStructureToolExclusions(
            AbstractPortableStructureToolScreen<?> screen,
            Consumer<Bounds> consumer
    ) {
        addUpgradePanelExclusion(screen, consumer);

        consumer.accept(new Bounds(
                screen.getGuiLeft() + SpatialOffsetControlsWidget.LEFT,
                screen.getGuiTop() + SpatialOffsetControlsWidget.TOP,
                SpatialOffsetControlsWidget.WIDTH,
                SpatialOffsetControlsWidget.HEIGHT
        ));
    }
}