package net.oktawia.spatialtoolscmp.compat.gtceu;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public interface GTCEuClientBridge {

    default void registerClient() {
    }

    default boolean isPipe(BlockState state) {
        return false;
    }

    default ModelData pipeModelData(int connectionMask, int blockedMask) {
        return ModelData.EMPTY;
    }

    default int connectionMask(ModelData modelData) {
        return 0;
    }
}
