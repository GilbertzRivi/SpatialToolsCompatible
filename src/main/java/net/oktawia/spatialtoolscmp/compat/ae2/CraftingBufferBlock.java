package net.oktawia.spatialtoolscmp.compat.ae2;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class CraftingBufferBlock extends AEBaseEntityBlock<CraftingBufferBlockEntity> {

    public CraftingBufferBlock() {
        super(BlockBehaviour.Properties.of().strength(2f).requiresCorrectToolForDrops());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingBufferBlockEntity(pos, state);
    }
}
