package net.oktawia.spatialtoolscmp.compat.gtceu;

import net.minecraft.world.item.ItemStack;

public interface GTCEuBridge {

    default void registerCommon() {
    }

    default boolean supportsPipeDirection(ItemStack target) {
        return false;
    }
}
