package net.oktawia.spatialtoolscmp.compat.gtceu;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public final class GTCEuClientCompat {

    private static final String BRIDGE_V7 = "net.oktawia.spatialtoolscmp.compat.gtceu.v7.GTCEuClientBridgeImpl";
    private static final String BRIDGE_V8 = "net.oktawia.spatialtoolscmp.compat.gtceu.v8.GTCEuClientBridgeImpl";

    private static final GTCEuClientBridge BRIDGE = GTCEuBridgeLoader.load(
            BRIDGE_V7,
            BRIDGE_V8,
            GTCEuClientBridge.class,
            new GTCEuClientBridge() {
            });

    private GTCEuClientCompat() {
    }

    public static void register() {
        if (!GTCEuVersion.LOADED) {
            return;
        }

        BRIDGE.registerClient();
    }

    public static boolean isPipe(BlockState state) {
        return BRIDGE.isPipe(state);
    }

    public static ModelData pipeModelData(int connectionMask, int blockedMask) {
        return BRIDGE.pipeModelData(connectionMask, blockedMask);
    }

    public static int connectionMask(ModelData modelData) {
        return BRIDGE.connectionMask(modelData);
    }
}
