package net.oktawia.spatialtoolscmp.compat.gtceu.v7;

import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.compat.gtceu.GTCEuBridge;
import net.oktawia.spatialtoolscmp.logic.PiperExtensions;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtensions;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;

public final class GTCEuBridgeImpl implements GTCEuBridge {

    @Override
    public void registerCommon() {
        GTCEuStructureExtension structureExtension = new GTCEuStructureExtension();

        StructureToolExtensions.registerClonerExtension(structureExtension);
        StructureToolExtensions.registerPasteExtension(structureExtension);
        StructureToolExtensions.registerRemoveExtension(structureExtension);
        ReplacerExtensions.register(new GTCEuReplacerExtension());
        PiperExtensions.register(new GTCEuPiperExtension());
    }

    @Override
    public boolean supportsPipeDirection(ItemStack target) {
        return GTCEuPiperExtension.supportsPipeDirection(target);
    }
}
