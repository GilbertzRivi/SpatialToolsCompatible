package net.oktawia.spatialtoolscmp.client.renderer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public record PreviewBlock(
        BlockPos pos,
        BlockState state,
        @Nullable CompoundTag blockEntityTag,
        @Nullable CompoundTag cloneMetadata) {
}
