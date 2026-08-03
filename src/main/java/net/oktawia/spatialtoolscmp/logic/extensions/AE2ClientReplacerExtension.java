package net.oktawia.spatialtoolscmp.logic.extensions;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.util.AECableType;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.client.render.cablebus.CableCoreType;
import appeng.core.definitions.AEBlocks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class AE2ClientReplacerExtension implements ClientReplacerExtension {

    @Override
    public boolean canHandleSource(ClientLevel level, BlockPos pos, BlockState state) {
        return AE2ReplacerExtension.centerCableItem(level, pos) != null;
    }

    @Override
    public @Nullable Set<BlockPos> computePreviewPositions(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ReplacerContext ctx
    ) {
        return AE2ReplacerExtension.findSameCablePositions(level, pos, ctx);
    }

    @Override
    public boolean needsReplacement(ClientLevel level, BlockPos pos, ItemStack target) {
        Item current = AE2ReplacerExtension.centerCableItem(level, pos);

        return current != null
                && AE2ReplacerExtension.isCablePartItem(target)
                && current != target.getItem();
    }

    @Override
    public @Nullable BlockState resolveTargetState(ItemStack target) {
        return AE2ReplacerExtension.isCablePartItem(target)
                ? AEBlocks.CABLE_BUS.block().defaultBlockState()
                : null;
    }

    @Override
    public @Nullable ModelData buildTargetModelData(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions
    ) {
        ICablePart cablePart = AE2ReplacerExtension.createCablePart(target);

        if (cablePart == null) {
            return null;
        }

        AECableType cableType = cablePart.getCableConnectionType();

        CableBusRenderState renderState = new CableBusRenderState();
        renderState.setCableColor(cablePart.getCableColor());
        renderState.setCableType(cableType);
        renderState.setCoreType(CableCoreType.fromCableType(cableType));
        renderState.setPos(pos);

        for (Direction side : Direction.values()) {
            if (!allPositions.contains(pos.relative(side))) {
                continue;
            }

            renderState.getConnectionTypes().put(side, cableType);
            renderState.getCableBusAdjacent().add(side);
            renderState.getChannelsOnSide().put(side, 0);
        }

        return ModelData.builder()
                .with(CableBusRenderState.PROPERTY, renderState)
                .build();
    }
}
