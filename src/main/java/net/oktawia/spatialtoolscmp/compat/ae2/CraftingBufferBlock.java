package net.oktawia.spatialtoolscmp.compat.ae2;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import appeng.block.AEBaseEntityBlock;

import net.oktawia.spatialtoolscmp.defs.LangDefs;

public class CraftingBufferBlock extends AEBaseEntityBlock<CraftingBufferBlockEntity> {

    public CraftingBufferBlock() {
        super(BlockBehaviour.Properties.of().strength(2f));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingBufferBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(LangDefs.CRAFTING_BUFFER_TOOLTIP_LINE1.getTranslationKey())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(LangDefs.CRAFTING_BUFFER_TOOLTIP_LINE2.getTranslationKey())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(LangDefs.CRAFTING_BUFFER_TOOLTIP_LINE3.getTranslationKey())
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.FAIL;
        }

        if (!(level.getBlockEntity(pos) instanceof CraftingBufferBlockEntity be)) {
            return InteractionResult.FAIL;
        }

        boolean initialHasError = be.hasDisplayError();
        var initialEntries = be.getDisplayEntries();

        Component title = Component.translatable(LangDefs.CRAFTING_BUFFER_BLOCK.getTranslationKey());

        NetworkHooks.openScreen(
                sp,
                new SimpleMenuProvider((id, inv, p) -> new CraftingBufferMenu(id, inv, p, be), title),
                buf -> {
                    buf.writeBoolean(initialHasError);
                    buf.writeVarInt(initialEntries.size());

                    for (var entry : initialEntries) {
                        buf.writeItem(entry.stack());
                        buf.writeLong(entry.requestedAmount());
                        buf.writeLong(entry.bufferedAmount());
                    }
                });

        return InteractionResult.CONSUME;
    }
}
