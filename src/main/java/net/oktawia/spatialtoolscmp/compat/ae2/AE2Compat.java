package net.oktawia.spatialtoolscmp.compat.ae2;

import appeng.api.features.GridLinkables;
import appeng.core.AppEng;
import appeng.core.definitions.AEItems;
import appeng.menu.locator.MenuLocators;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;

public final class AE2Compat {

    private AE2Compat() {}

    public static boolean isEnergyUpgradeItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == AEItems.ENERGY_CARD.asItem();
    }

    public static boolean isCraftingUpgradeItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == AEItems.CRAFTING_CARD.asItem();
    }

    public static Component energyUpgradeDisplayName() {
        return AEItems.ENERGY_CARD.stack().getHoverName();
    }

    public static Component craftingUpgradeDisplayName() {
        return AEItems.CRAFTING_CARD.stack().getHoverName();
    }

    public static Component modDisplayName() {
        return Component.literal(AppEng.MOD_NAME);
    }

    public static void register() {
        GridLinkables.register(SpatialItemRegistrar.PORTABLE_SPATIAL_CLONER.get(), AE2GridLinkableHandler.INSTANCE);
        GridLinkables.register(SpatialItemRegistrar.PORTABLE_SPATIAL_REPLACER.get(), AE2GridLinkableHandler.INSTANCE);
        MenuLocators.register(
                AE2ClonerCraftingLocator.class,
                AE2ClonerCraftingLocator::writeToPacket,
                AE2ClonerCraftingLocator::readFromPacket
        );
    }
}