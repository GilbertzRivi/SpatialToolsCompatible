package net.oktawia.spatialtoolscmp.logic.extensions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtension;
import net.oktawia.spatialtoolscmp.logic.ClientReplacerExtension;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class FramedBlocksClientExtension
        implements ClientReplacerExtension, ClientPiperExtension {

    private CompoundTag cachedCamo = null;
    private BlockState cachedState = null;
    private ModelData cachedModelData = null;

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
        if (!FramedBlocksReplacerExtension.isFramedTarget(target)) {
            return null;
        }

        return FramedBlocksReplacerExtension.blockStateOf(target);
    }

    @Override
    public boolean needsReplacement(ClientLevel level, BlockPos pos, ItemStack target) {
        return FramedBlocksReplacerExtension.needsFramedUpdate(level, pos, target);
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
        if (!FramedBlocksReplacerExtension.isFramedTarget(target)) {
            return null;
        }

        CompoundTag camo = FramedBlocksReplacerExtension.targetCamo(target);

        if (camo == null) {
            camo = new CompoundTag();
        }

        if (camo.equals(this.cachedCamo) && targetState.equals(this.cachedState)) {
            return this.cachedModelData;
        }

        this.cachedCamo = camo.copy();
        this.cachedState = targetState;
        this.cachedModelData = buildCamoModelData(targetState, camo, pos);

        return this.cachedModelData;
    }

    @Nullable
    private static ModelData buildCamoModelData(
            BlockState targetState,
            CompoundTag camo,
            BlockPos pos
    ) {
        if (!(targetState.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }

        try {
            BlockEntity be = entityBlock.newBlockEntity(pos, targetState);

            if (be == null) {
                return null;
            }

            ClientLevel level = Minecraft.getInstance().level;

            if (level != null) {
                be.setLevel(level);
            }

            CompoundTag beTag = camo.copy();
            beTag.putInt("x", pos.getX());
            beTag.putInt("y", pos.getY());
            beTag.putInt("z", pos.getZ());

            be.load(beTag);

            if (level != null) {
                be.setLevel(level);
            }

            return be.getModelData();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
