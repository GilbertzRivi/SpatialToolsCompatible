package net.oktawia.spatialtoolscmp.compat.gtceu.v8;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.client.model.GTModelProperties;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import net.oktawia.spatialtoolscmp.client.renderer.BlockRenderExtensions;
import net.oktawia.spatialtoolscmp.compat.gtceu.GTCEuClientBridge;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtensions;

public final class GTCEuClientBridgeImpl implements GTCEuClientBridge {

    @Override
    public void registerClient() {
        BlockRenderExtensions.register(new GTCEuBlockRenderExtension());
        ClientReplacerExtensions.register(new GTCEuClientReplacerExtension());
    }

    @Override
    public boolean isPipe(BlockState state) {
        return state.getBlock() instanceof PipeBlock<?, ?, ?>;
    }

    @Override
    public ModelData pipeModelData(int connectionMask, int blockedMask) {
        return ModelData.builder()
                .with(GTModelProperties.PIPE_CONNECTION_MASK, connectionMask)
                .with(GTModelProperties.PIPE_BLOCKED_MASK, blockedMask)
                .build();
    }

    @Override
    public int connectionMask(ModelData modelData) {
        return modelData.has(GTModelProperties.PIPE_CONNECTION_MASK)
                ? modelData.get(GTModelProperties.PIPE_CONNECTION_MASK)
                : 0;
    }
}
