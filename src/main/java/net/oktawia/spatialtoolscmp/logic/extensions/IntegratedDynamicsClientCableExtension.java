package net.oktawia.spatialtoolscmp.logic.extensions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtension;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import org.cyclops.integrateddynamics.block.BlockCable;
import org.cyclops.integrateddynamics.core.helper.CableHelpers;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

public final class IntegratedDynamicsClientCableExtension
        implements ClientReplacerExtension, ClientPiperExtension {

    @Override
    public boolean canHandleSource(ClientLevel level, BlockPos pos, BlockState state) {
        return false;
    }

    @Nullable
    @Override
    public Set<BlockPos> computePreviewPositions(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ReplacerContext ctx
    ) {
        return null;
    }

    @Nullable
    @Override
    public BlockState resolveTargetState(ItemStack target) {
        return null;
    }

    @Nullable
    @Override
    public Iterable<RenderType> getPreviewRenderTypes(BlockState targetState, ItemStack target) {
        return null;
    }

    @Nullable
    @Override
    public ModelData buildTargetModelData(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions
    ) {
        if (!(targetState.getBlock() instanceof BlockCable)) {
            return null;
        }

        ClientLevel level = Minecraft.getInstance().level;

        ModelData.Builder builder = ModelData.builder()
                .with(BlockCable.REALCABLE, true)
                .with(BlockCable.FACADE, Optional.empty());

        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);

            boolean connected = allPositions.contains(neighbor)
                    || connectsToWorld(level, neighbor, side.getOpposite());

            builder.with(BlockCable.CONNECTED[side.ordinal()], connected);
        }

        return builder.build();
    }

    private static boolean connectsToWorld(
            @Nullable ClientLevel level,
            BlockPos neighborPos,
            Direction neighborSide
    ) {
        if (level == null) {
            return false;
        }

        try {
            return CableHelpers.getCable(level, neighborPos, neighborSide).isPresent()
                    && CableHelpers.isNoFakeCable(level, neighborPos, neighborSide);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
