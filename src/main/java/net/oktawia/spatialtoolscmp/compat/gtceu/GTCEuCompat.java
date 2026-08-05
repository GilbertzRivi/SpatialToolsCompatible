package net.oktawia.spatialtoolscmp.compat.gtceu;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;

public final class GTCEuCompat {

    private static final String BRIDGE_V7 = "net.oktawia.spatialtoolscmp.compat.gtceu.v7.GTCEuBridgeImpl";
    private static final String BRIDGE_V8 = "net.oktawia.spatialtoolscmp.compat.gtceu.v8.GTCEuBridgeImpl";

    private static final GTCEuBridge BRIDGE = GTCEuBridgeLoader.load(
            BRIDGE_V7,
            BRIDGE_V8,
            GTCEuBridge.class,
            new GTCEuBridge() {
            });

    private GTCEuCompat() {
    }

    public static void registerCommon() {
        if (!GTCEuVersion.LOADED) {
            return;
        }

        GTCEuVersion.logDetection();
        BRIDGE.registerCommon();
    }

    public static boolean supportsPipeDirection(ItemStack target) {
        return !target.isEmpty() && BRIDGE.supportsPipeDirection(target);
    }

    public static int blockedMask(
            @Nullable Direction step,
            PortableSpatialPiper.PipeDirectionMode directionMode) {
        Direction blocked = blockedFace(step, directionMode);

        return blocked == null ? 0 : 1 << blocked.ordinal();
    }

    @Nullable
    public static Direction blockedFace(
            @Nullable Direction step,
            PortableSpatialPiper.PipeDirectionMode directionMode) {
        if (step == null) {
            return null;
        }

        return switch (directionMode) {
            case OFF -> null;
            case ALONG_PATH -> step;
            case AGAINST_PATH -> step.getOpposite();
        };
    }
}
