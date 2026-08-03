package net.oktawia.spatialtoolscmp.client.misc.widgets;

import java.util.HashMap;
import java.util.Map;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewStructure;

public class PortableSpatialStorageDummyWorld extends TrackedDummyWorld {

    private final Map<BlockPos, BlockEntity> previewBlockEntities = new HashMap<>();

    public void loadPreviewStructure(@Nullable PreviewStructure structure) {
        this.clear();
        this.previewBlockEntities.clear();

        if (structure == null) {
            return;
        }

        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return;
        }

        for (PreviewBlock block : structure.blocks()) {
            this.setBlock(block.pos(), block.state(), 3);
        }

        Map<BlockPos, BlockEntity> builtBlockEntities = structure.blockEntities(clientLevel);
        for (Map.Entry<BlockPos, BlockEntity> entry : builtBlockEntities.entrySet()) {
            BlockEntity blockEntity = entry.getValue();

            try {
                blockEntity.setLevel(this);
            } catch (Throwable ignored) {
            }

            try {
                blockEntity.clearRemoved();
            } catch (Throwable ignored) {
            }

            try {
                blockEntity.onLoad();
            } catch (Throwable ignored) {
            }

            this.previewBlockEntities.put(entry.getKey(), blockEntity);
        }
    }

    private static boolean isSafeToRenderAsTesr(BlockEntity blockEntity) {
        BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher()
                .getRenderer(blockEntity);

        if (renderer == null) {
            return true;
        }

        try {
            return renderer.shouldRender(blockEntity, Vec3.atCenterOf(blockEntity.getBlockPos()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        BlockEntity preview = this.previewBlockEntities.get(pos);
        BlockEntity be = preview != null ? preview : super.getBlockEntity(pos);

        if (be == null || !isSafeToRenderAsTesr(be)) {
            return null;
        }

        return be;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return super.getBlockState(pos);
    }
}
