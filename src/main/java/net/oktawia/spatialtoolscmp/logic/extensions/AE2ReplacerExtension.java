package net.oktawia.spatialtoolscmp.logic.extensions;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.core.definitions.AEBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.items.helpers.ClonerBlockPlacer;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtension;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Set;

public final class AE2ReplacerExtension implements ReplacerExtension {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String CABLE_CENTER_KEY = "cable";

    @Override
    public boolean canHandleSource(ServerLevel level, BlockPos pos, BlockState state) {
        return centerCableItem(level, pos) != null;
    }

    @Override
    public Set<BlockPos> findReplacementTargets(
            ServerLevel level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx
    ) {
        return findSameCablePositions(level, startPos, ctx);
    }

    public static Set<BlockPos> findSameCablePositions(
            BlockGetter level,
            BlockPos startPos,
            ReplacerContext ctx
    ) {
        Item sourceCable = centerCableItem(level, startPos);

        if (sourceCable == null) {
            return Collections.emptySet();
        }

        return PortableSpatialReplacer.floodFill(
                level,
                startPos,
                ctx,
                (checkedLevel, pos) -> centerCableItem(checkedLevel, pos) == sourceCable
        );
    }

    @Override
    public @Nullable Block resolveTargetBlock(ServerLevel level, ItemStack target) {
        return isCablePartItem(target) ? AEBlocks.CABLE_BUS.block() : null;
    }

    @Override
    public @Nullable ItemStack pickTargetItem(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof IPartHost host)) {
            return null;
        }

        IPart cable = host.getPart(null);

        return cable != null ? new ItemStack(cable.getPartItem()) : null;
    }

    @Override
    public boolean isUnplaceableTarget(ServerLevel level, Block targetBlock, ItemStack target) {
        return targetBlock == AEBlocks.CABLE_BUS.block() && !isCablePartItem(target);
    }

    @Override
    public boolean needsReplacement(ServerLevel level, BlockPos pos, ItemStack target) {
        Item current = centerCableItem(level, pos);

        return current != null && isCablePartItem(target) && current != target.getItem();
    }

    @Override
    public @Nullable ItemStack getInPlaceSwapItem(ServerLevel level, BlockPos pos, ItemStack target) {
        Item current = centerCableItem(level, pos);

        return current != null && isCablePartItem(target) ? new ItemStack(current) : null;
    }

    @Override
    public boolean placeTarget(
            ServerLevel level,
            BlockPos pos,
            ItemStack target,
            @Nullable ServerPlayer player
    ) {
        IPartItem<?> partItem = asCablePartItem(target);

        if (partItem == null) {
            return false;
        }

        if (level.getBlockEntity(pos) instanceof IPartHost existingHost
                && existingHost.getPart(null) != null) {
            IPartItem<?> previousItem = existingHost.getPart(null).getPartItem();

            if (swapCenterCable(existingHost, partItem, player) != null) {
                return true;
            }

            swapCenterCable(existingHost, previousItem, player);

            LOGGER.warn(
                    "Cable {} at {} is not compatible with the parts on this bus",
                    IPartItem.getId(partItem),
                    pos
            );

            return false;
        }

        BlockState oldState = level.getBlockState(pos);
        BlockState busState = AEBlocks.CABLE_BUS.block().getStateForPlacement(level, pos);

        CompoundTag beTag = new CompoundTag();
        CompoundTag cableTag = new CompoundTag();

        cableTag.putString("id", IPartItem.getId(partItem).toString());
        beTag.put(CABLE_CENTER_KEY, cableTag);

        boolean placed = ClonerBlockPlacer.replaceBlockAndLoadTag(level, pos, busState, beTag);
        BlockEntity be = level.getBlockEntity(pos);
        IPart cable = be instanceof IPartHost host ? host.getPart(null) : null;

        if (placed && cable != null) {
            return true;
        }

        LOGGER.warn(
                "Failed to place {} at {}: placed={} blockEntity={}",
                IPartItem.getId(partItem),
                pos,
                placed,
                be
        );

        level.setBlock(pos, oldState, Block.UPDATE_ALL);
        return false;
    }

    private static <T extends IPart> T swapCenterCable(
            IPartHost host,
            IPartItem<T> partItem,
            @Nullable ServerPlayer player
    ) {
        return host.replacePart(partItem, null, player, null);
    }

    @Nullable
    public static Item centerCableItem(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof IPartHost host)) {
            return null;
        }

        IPart cable = host.getPart(null);

        return cable instanceof ICablePart ? cable.getPartItem().asItem() : null;
    }

    public static boolean isCablePartItem(ItemStack stack) {
        return asCablePartItem(stack) != null;
    }

    @Nullable
    private static IPartItem<?> asCablePartItem(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IPartItem<?> partItem)) {
            return null;
        }

        return ICablePart.class.isAssignableFrom(partItem.getPartClass()) ? partItem : null;
    }

    @Nullable
    public static ICablePart createCablePart(ItemStack stack) {
        IPartItem<?> partItem = asCablePartItem(stack);

        if (partItem == null) {
            return null;
        }

        try {
            IPart part = partItem.createPart();

            return part instanceof ICablePart cablePart ? cablePart : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
