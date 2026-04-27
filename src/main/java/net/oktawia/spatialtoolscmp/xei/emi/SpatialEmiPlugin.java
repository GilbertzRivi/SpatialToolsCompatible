package net.oktawia.spatialtoolscmp.xei.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PowerUpgradePanelWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SpatialOffsetControlsWidget;
import net.oktawia.spatialtoolscmp.client.screens.AbstractPortableStructureToolScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialClonerScreen;
import net.oktawia.spatialtoolscmp.client.screens.PortableSpatialStorageScreen;

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
    }

    private static void addStructureToolExclusions(
            AbstractPortableStructureToolScreen<?> screen,
            Consumer<Bounds> consumer
    ) {
        consumer.accept(new Bounds(
                screen.getGuiLeft() + SpatialOffsetControlsWidget.LEFT,
                screen.getGuiTop() + SpatialOffsetControlsWidget.TOP,
                SpatialOffsetControlsWidget.WIDTH,
                SpatialOffsetControlsWidget.HEIGHT
        ));

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
}