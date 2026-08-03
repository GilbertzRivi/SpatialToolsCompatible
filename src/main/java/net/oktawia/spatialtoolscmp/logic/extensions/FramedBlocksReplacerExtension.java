package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.oktawia.spatialtoolscmp.items.helpers.ClonerBlockPlacer;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtension;

public final class FramedBlocksReplacerExtension implements ReplacerExtension {

    private static final String TARGET_STATE_KEY = "spatialFramedState";
    private static final String TARGET_BLOCKSTATE_KEY = "spatialFramedBlockState";

    private static final String NBT_GLOWING = "glowing";

    private static final Set<String> SKIPPED_KEYS = Set.of("id", "x", "y", "z");

    @Override
    public boolean canHandleSource(ServerLevel level, BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public Set<BlockPos> findReplacementTargets(
            ServerLevel level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx) {
        return Set.of();
    }

    @Nullable
    @Override
    public ItemStack pickTargetItem(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag beTag = FramedBlocksClonerExtension.saveCurrentTag(be);

        if (!FramedBlocksClonerExtension.isFramedBlock(state, beTag)) {
            return null;
        }

        ItemStack frame = new ItemStack(state.getBlock());

        if (frame.isEmpty()) {
            return null;
        }

        CompoundTag framedState = extractFramedState(beTag);

        if (!framedState.isEmpty()) {
            frame.getOrCreateTag().put(TARGET_STATE_KEY, framedState);
        }

        frame.getOrCreateTag().put(TARGET_BLOCKSTATE_KEY, NbtUtils.writeBlockState(state));

        return frame;
    }

    @Override
    public boolean isUnplaceableTarget(ServerLevel level, Block targetBlock, ItemStack target) {
        return isFramedTarget(target);
    }

    @Nullable
    @Override
    public Block resolveTargetBlock(ServerLevel level, ItemStack target) {
        if (!isFramedTarget(target)) {
            return null;
        }

        BlockState state = blockStateOf(target);

        return state != null ? state.getBlock() : null;
    }

    @Nullable
    @Override
    public List<ItemStack> getPlacementCost(ServerLevel level, ItemStack target) {
        if (!isFramedTarget(target)) {
            return null;
        }

        List<ItemStack> cost = new ArrayList<>();
        cost.add(strippedFrame(target));

        CompoundTag camo = targetCamo(target);

        if (camo == null) {
            return cost;
        }

        for (String[] camoKey : FramedBlocksClonerExtension.CAMO_KEYS) {
            if (!camo.contains(camoKey[0], Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack camoItem = FramedBlocksClonerExtension.getCamoRequirement(camo.getCompound(camoKey[0]));

            if (!camoItem.isEmpty()) {
                cost.add(camoItem);
            }
        }

        if (camo.getBoolean(NBT_GLOWING)) {
            cost.add(new ItemStack(Items.GLOWSTONE_DUST));
        }

        return cost;
    }

    @Override
    public boolean needsReplacement(ServerLevel level, BlockPos pos, ItemStack target) {
        return needsFramedUpdate(level, pos, target);
    }

    static boolean needsFramedUpdate(BlockGetter level, BlockPos pos, ItemStack target) {
        if (!isFramedTarget(target)) {
            return false;
        }

        BlockState targetState = blockStateOf(target);
        BlockState currentState = level.getBlockState(pos);

        if (targetState == null || currentState.getBlock() != targetState.getBlock()) {
            return false;
        }

        if (!currentState.equals(targetState)) {
            return true;
        }

        CompoundTag wanted = targetCamo(target);

        if (wanted == null) {
            return false;
        }

        CompoundTag current = extractFramedState(
                FramedBlocksClonerExtension.saveCurrentTag(level.getBlockEntity(pos)));

        return !current.equals(wanted);
    }

    @Override
    public boolean placeTarget(
            ServerLevel level,
            BlockPos pos,
            ItemStack target,
            @Nullable ServerPlayer player) {
        if (!isFramedTarget(target)) {
            return false;
        }

        BlockState state = blockStateOf(target);

        if (state == null) {
            return false;
        }

        CompoundTag camo = targetCamo(target);

        return ClonerBlockPlacer.replaceBlockAndLoadTag(
                level,
                pos,
                state,
                camo == null ? null : camo.copy());
    }

    @Nullable
    static BlockState blockStateOf(ItemStack target) {
        CompoundTag tag = target.getTag();

        if (tag != null && tag.contains(TARGET_BLOCKSTATE_KEY, Tag.TAG_COMPOUND)) {
            try {
                BlockState state = NbtUtils.readBlockState(
                        BuiltInRegistries.BLOCK.asLookup(),
                        tag.getCompound(TARGET_BLOCKSTATE_KEY));

                if (!state.isAir()) {
                    return state;
                }
            } catch (Throwable ignored) {
            }
        }

        if (!(target.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        return blockItem.getBlock().defaultBlockState();
    }

    static boolean isFramedTarget(ItemStack target) {
        CompoundTag tag = target.getTag();

        if (tag == null) {
            return false;
        }

        return tag.contains(TARGET_STATE_KEY, Tag.TAG_COMPOUND)
                || tag.contains(TARGET_BLOCKSTATE_KEY, Tag.TAG_COMPOUND);
    }

    @Nullable
    static CompoundTag targetCamo(ItemStack target) {
        CompoundTag tag = target.getTag();

        if (tag == null || !tag.contains(TARGET_STATE_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag camo = tag.getCompound(TARGET_STATE_KEY);

        return camo.isEmpty() ? null : camo;
    }

    private static ItemStack strippedFrame(ItemStack target) {
        ItemStack frame = target.copy();
        frame.setCount(1);

        CompoundTag tag = frame.getTag();

        if (tag != null) {
            tag.remove(TARGET_STATE_KEY);
            tag.remove(TARGET_BLOCKSTATE_KEY);

            if (tag.isEmpty()) {
                frame.setTag(null);
            }
        }

        return frame;
    }

    static CompoundTag extractFramedState(@Nullable CompoundTag beTag) {
        CompoundTag framedState = new CompoundTag();

        if (beTag == null) {
            return framedState;
        }

        for (String key : beTag.getAllKeys()) {
            if (SKIPPED_KEYS.contains(key)) {
                continue;
            }

            Tag value = beTag.get(key);

            if (value != null) {
                framedState.put(key, value.copy());
            }
        }

        return framedState;
    }
}
