package net.oktawia.spatialtoolscmp.items.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructureToolExtensions;
import net.oktawia.spatialtoolscmp.logic.StructureToolStructureStore;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClonerUndoHandler {

    private static final String UNDO_DIMENSION_KEY = "clonerUndoDimension";
    private static final String UNDO_BLOCKS_KEY = "clonerUndoBlocks";
    public static final String UNDO_ID_KEY = "clonerUndoId";

    private static final String POS_X = "x";
    private static final String POS_Y = "y";
    private static final String POS_Z = "z";
    private static final String STATE_KEY = "state";
    private static final String REFUNDS_KEY = "refunds";
    private static final String REFUND_STACK_KEY = "stack";
    private static final String REFUND_COUNT_KEY = "count";

    private static final int UNDO_CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private ClonerUndoHandler() {
    }

    public record ClonerUndoPlacedBlock(BlockPos pos, String stateSignature, List<ItemStack> refundStacks) {
    }

    public static void store(ItemStack toolStack, ServerLevel level, List<ClonerUndoPlacedBlock> placedBlocks) {
        clear(level, toolStack);

        CompoundTag undoTag = new CompoundTag();
        undoTag.putString(UNDO_DIMENSION_KEY, level.dimension().location().toString());

        ListTag blocksTag = new ListTag();

        for (ClonerUndoPlacedBlock placedBlock : placedBlocks) {
            CompoundTag blockTag = new CompoundTag();

            BlockState currentState = level.getBlockState(placedBlock.pos());

            blockTag.putInt(POS_X, placedBlock.pos().getX());
            blockTag.putInt(POS_Y, placedBlock.pos().getY());
            blockTag.putInt(POS_Z, placedBlock.pos().getZ());
            blockTag.putString(STATE_KEY, getBlockId(currentState));

            ListTag refundList = new ListTag();

            for (ItemStack refundStack : placedBlock.refundStacks()) {
                if (refundStack.isEmpty()) {
                    continue;
                }

                CompoundTag refundEntry = new CompoundTag();
                ItemStack single = refundStack.copy();
                int count = Math.max(1, refundStack.getCount());
                single.setCount(1);

                refundEntry.put(REFUND_STACK_KEY, single.save(new CompoundTag()));
                refundEntry.putInt(REFUND_COUNT_KEY, count);
                refundList.add(refundEntry);
            }

            blockTag.put(REFUNDS_KEY, refundList);
            blocksTag.add(blockTag);
        }

        undoTag.put(UNDO_BLOCKS_KEY, blocksTag);

        String undoId = UUID.randomUUID().toString();

        try {
            StructureToolStructureStore.save(level.getServer(), undoId, undoTag);
            toolStack.getOrCreateTag().putString(UNDO_ID_KEY, undoId);
        } catch (IOException ignored) {
        }
    }

    public static @Nullable CompoundTag load(ServerLevel level, ItemStack toolStack) {
        CompoundTag tag = toolStack.getTag();

        if (tag == null || !tag.contains(UNDO_ID_KEY, Tag.TAG_STRING)) {
            return null;
        }

        String undoId = tag.getString(UNDO_ID_KEY);

        if (undoId.isBlank()) {
            return null;
        }

        try {
            return StructureToolStructureStore.load(level.getServer(), undoId);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static void clear(ServerLevel level, ItemStack toolStack) {
        CompoundTag tag = toolStack.getTag();

        if (tag == null) {
            return;
        }

        if (tag.contains(UNDO_ID_KEY, Tag.TAG_STRING)) {
            String undoId = tag.getString(UNDO_ID_KEY);

            if (!undoId.isBlank()) {
                try {
                    StructureToolStructureStore.delete(level.getServer(), undoId);
                } catch (IOException ignored) {
                }
            }
        }

        clearFromStack(toolStack);
    }

    public static void clearFromStack(ItemStack toolStack) {
        CompoundTag tag = toolStack.getTag();

        if (tag == null) {
            return;
        }

        tag.remove(UNDO_ID_KEY);
        tag.remove(UNDO_DIMENSION_KEY);
        tag.remove(UNDO_BLOCKS_KEY);
    }

    public static String getDimension(CompoundTag undoTag) {
        return undoTag.getString(UNDO_DIMENSION_KEY);
    }

    public static boolean hasBlocks(CompoundTag undoTag) {
        return undoTag.contains(UNDO_BLOCKS_KEY, Tag.TAG_LIST);
    }

    public static List<ClonerUndoPlacedBlock> readBlocks(CompoundTag undoTag) {
        if (!undoTag.contains(UNDO_BLOCKS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag blocksTag = undoTag.getList(UNDO_BLOCKS_KEY, Tag.TAG_COMPOUND);
        List<ClonerUndoPlacedBlock> out = new ArrayList<>();

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);

            BlockPos pos = new BlockPos(
                    blockTag.getInt(POS_X),
                    blockTag.getInt(POS_Y),
                    blockTag.getInt(POS_Z)
            );

            String stateSignature = blockTag.getString(STATE_KEY);
            List<ItemStack> refundStacks = new ArrayList<>();

            if (blockTag.contains(REFUNDS_KEY, Tag.TAG_LIST)) {
                ListTag refundList = blockTag.getList(REFUNDS_KEY, Tag.TAG_COMPOUND);

                for (int j = 0; j < refundList.size(); j++) {
                    CompoundTag refundEntry = refundList.getCompound(j);

                    if (!refundEntry.contains(REFUND_STACK_KEY, Tag.TAG_COMPOUND)) {
                        continue;
                    }

                    ItemStack stack = ItemStack.of(refundEntry.getCompound(REFUND_STACK_KEY));
                    int count = Math.max(1, refundEntry.getInt(REFUND_COUNT_KEY));

                    if (!stack.isEmpty()) {
                        stack.setCount(count);
                        refundStacks.add(stack);
                    }
                }
            }

            out.add(new ClonerUndoPlacedBlock(pos, stateSignature, List.copyOf(refundStacks)));
        }

        return out;
    }

    public static boolean areBlocksUnchanged(ServerLevel level, List<ClonerUndoPlacedBlock> undoBlocks) {
        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            BlockState currentState = level.getBlockState(undoBlock.pos());

            if (!matchesStoredBlock(currentState, undoBlock.stateSignature())) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesStoredBlock(BlockState currentState, String storedSignature) {
        if (currentState.isAir()) {
            return false;
        }

        String currentBlockId = getBlockId(currentState);
        String storedBlockId = extractBlockId(storedSignature);

        return !currentBlockId.isBlank()
                && !storedBlockId.isBlank()
                && currentBlockId.equals(storedBlockId);
    }

    private static String getBlockId(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (key != null) {
            return key.toString();
        }

        return extractBlockId(state.toString());
    }

    private static String extractBlockId(String signature) {
        if (signature == null || signature.isBlank()) {
            return "";
        }

        int braceStart = signature.indexOf('{');
        int braceEnd = signature.indexOf('}', braceStart + 1);

        if (braceStart >= 0 && braceEnd > braceStart) {
            return signature.substring(braceStart + 1, braceEnd);
        }

        int bracketStart = signature.indexOf('[');

        if (bracketStart > 0) {
            return signature.substring(0, bracketStart);
        }

        return signature;
    }

    public static List<ItemStack> collectCurrentRefundStacks(
            ServerLevel level,
            List<ClonerUndoPlacedBlock> undoBlocks
    ) {
        List<ItemStack> out = new ArrayList<>();

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            BlockPos pos = undoBlock.pos();
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(pos);
            boolean handled = false;

            for (StructureCloneExtension extension : StructureToolExtensions.clonerExtensions()) {
                try {
                    if (extension.collectUndoRefunds(level, pos, state, be, out)) {
                        handled = true;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (handled) {
                continue;
            }

            ItemStack stack = ClonerBlockPlacer.getRequiredBlockItem(state);

            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }

        return aggregateRefundStacks(out);
    }

    public static void removeBlocks(ServerLevel level, List<ClonerUndoPlacedBlock> undoBlocks) {
        BlockState air = Blocks.AIR.defaultBlockState();

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            level.removeBlockEntity(undoBlock.pos());
        }

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            if (level.getBlockState(undoBlock.pos()).isAir()) {
                continue;
            }

            level.setBlock(undoBlock.pos(), air, UNDO_CLEAR_FLAGS, 0);
        }
    }

    public static List<ItemStack> collectRefundStacks(List<ClonerUndoPlacedBlock> undoBlocks) {
        List<ItemStack> out = new ArrayList<>();

        for (ClonerUndoPlacedBlock undoBlock : undoBlocks) {
            for (ItemStack stack : undoBlock.refundStacks()) {
                if (!stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
        }

        return aggregateRefundStacks(out);
    }

    public static List<ItemStack> aggregateRefundStacks(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack copy = stack.copy();
            boolean merged = false;

            for (ItemStack existing : out) {
                if (ItemStack.isSameItemSameTags(existing, copy)) {
                    existing.grow(copy.getCount());
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                out.add(copy);
            }
        }

        return out;
    }
}