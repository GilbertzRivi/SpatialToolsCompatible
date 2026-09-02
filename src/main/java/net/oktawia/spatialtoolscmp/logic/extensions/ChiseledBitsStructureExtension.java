package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import mod.chiselsandbits.api.block.entity.IMultiStateBlockEntity;
import mod.chiselsandbits.api.blockinformation.IBlockInformation;
import mod.chiselsandbits.api.multistate.StateEntrySize;
import mod.chiselsandbits.api.multistate.statistics.IMultiStateObjectStatistics;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructurePasteExtension;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public final class ChiseledBitsStructureExtension implements StructureCloneExtension, StructurePasteExtension {

    private static final String NBT_ID = "id";
    private static final String NBT_DATA = "data";
    private static final String NBT_VERSION = "version";

    private static final String CLONE_KEY_MATERIALS = "materials";
    private static final String CLONE_KEY_MAT_ID = "id";
    private static final String CLONE_KEY_MAT_COUNT = "count";
    private static final String CLONE_KEY_BE = "be";

    private static final int MAX_LOAD_WAIT_TICKS = 40;
    private static final int RESTORE_WAIT_TICKS = 2;

    private static final List<PendingApply> PENDING = new CopyOnWriteArrayList<>();
    private static volatile boolean registered = false;

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isChiseled(null, rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        if (!isChiseled(be, rawBeTag)) {
            return false;
        }

        CompoundTag chiseledData = new CompoundTag();

        CompoundTag engineTag = extractEngineTag(rawBeTag, be);
        if (engineTag != null) {
            chiseledData.put(CLONE_KEY_BE, engineTag);
        }

        Map<Item, Integer> materials = computeMaterials(be);
        ListTag materialList = new ListTag();

        for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(entry.getKey());

            if (itemId == null) {
                continue;
            }

            requirements.add(new ItemStack(entry.getKey(), entry.getValue()));

            CompoundTag row = new CompoundTag();
            row.putString(CLONE_KEY_MAT_ID, itemId.toString());
            row.putInt(CLONE_KEY_MAT_COUNT, entry.getValue());
            materialList.add(row);
        }

        if (!materialList.isEmpty()) {
            chiseledData.put(CLONE_KEY_MATERIALS, materialList);
        }

        blockEntry.put(StructureToolKeys.CLONE_KEY_CHISELED, chiseledData);
        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
        if (!isChiseled(null, rawBeTag)) {
            return Optional.empty();
        }

        CompoundTag chiseledData = getChiseledMetadata(blockMetadata);

        if (!chiseledData.contains(CLONE_KEY_BE, Tag.TAG_COMPOUND)) {
            return Optional.of(PlacementPlan.none());
        }

        List<ItemStack> costs = new ArrayList<>();

        if (!player.isCreative()) {
            Map<Item, Integer> reserved = new LinkedHashMap<>();
            ListTag materialList = chiseledData.getList(CLONE_KEY_MATERIALS, Tag.TAG_COMPOUND);

            for (int i = 0; i < materialList.size(); i++) {
                CompoundTag row = materialList.getCompound(i);
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(row.getString(CLONE_KEY_MAT_ID)));

                if (item == null || item == Items.AIR) {
                    continue;
                }

                int amount = Math.max(1, row.getInt(CLONE_KEY_MAT_COUNT));
                ItemStack cost = new ItemStack(item, amount);

                if (!ctx.canReserveForPaste(reserved, cost, amount)) {
                    return Optional.of(PlacementPlan.none());
                }

                costs.add(cost);
            }
        }

        return Optional.of(new PlacementPlan(true, state, null, costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        applyChiseledData(level, pos, be, getChiseledMetadata(blockMetadata));
    }

    @Override
    public void onTemplatePasted(ServerLevel level, BlockPos placementOrigin, CompoundTag templateTag) {
        if (!templateTag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        Map<BlockPos, BlockState> templateStates = new LinkedHashMap<>();

        for (TemplateUtil.BlockInfo info : TemplateUtil.parseRawBlocksFromTag(templateTag)) {
            templateStates.put(info.pos(), info.state());
        }

        CompoundTag metadata = templateTag.getCompound(StructureToolKeys.CLONE_METADATA_KEY);
        ListTag blocks = metadata.getList(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockEntry = blocks.getCompound(i);

            if (!blockEntry.contains(StructureToolKeys.CLONE_KEY_CHISELED, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag posTag = blockEntry.getCompound(StructureToolKeys.CLONE_KEY_POS);
            BlockPos localPos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z"));

            queueTransform(
                    level,
                    placementOrigin.offset(localPos).immutable(),
                    blockEntry.getCompound(StructureToolKeys.CLONE_KEY_CHISELED),
                    templateStates.get(localPos));
        }
    }

    private static void queueTransform(
            ServerLevel level,
            BlockPos pos,
            CompoundTag chiseledData,
            @Nullable BlockState templateState) {
        if (!chiseledData.contains(CLONE_KEY_BE, Tag.TAG_COMPOUND)) {
            return;
        }

        ensureRegistered();
        PENDING.add(new PendingApply(
                level,
                pos.immutable(),
                chiseledData.getIntArray(StructureToolKeys.CHISELED_KEY_OPS),
                chiseledData.getCompound(CLONE_KEY_BE).copy(),
                templateState,
                level.getGameTime()));
    }

    private static void applyChiseledData(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            CompoundTag chiseledData) {
        if (!(be instanceof IMultiStateBlockEntity multiState)) {
            return;
        }

        if (!chiseledData.contains(CLONE_KEY_BE, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag engineTag = chiseledData.getCompound(CLONE_KEY_BE).copy();
        int[] ops = chiseledData.getIntArray(StructureToolKeys.CHISELED_KEY_OPS);

        multiState.deserializeNBT(engineTag);

        ensureRegistered();
        PENDING.add(new PendingApply(level, pos.immutable(), ops, null, null, level.getGameTime()));
    }

    @Nullable
    private static CompoundTag extractEngineTag(@Nullable CompoundTag rawBeTag, BlockEntity be) {
        CompoundTag source = rawBeTag;

        if ((source == null || !source.contains(NBT_DATA, Tag.TAG_COMPOUND))
                && be instanceof IMultiStateBlockEntity multiState) {
            try {
                source = multiState.serializeNBT();
            } catch (Throwable ignored) {
                source = null;
            }
        }

        if (source == null || !source.contains(NBT_DATA, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag engineTag = new CompoundTag();
        engineTag.putString(NBT_ID, StructureToolKeys.CHISELED_BE_ID);
        engineTag.put(NBT_DATA, source.getCompound(NBT_DATA).copy());

        if (source.contains(NBT_VERSION)) {
            Tag version = source.get(NBT_VERSION);
            if (version != null) {
                engineTag.put(NBT_VERSION, version.copy());
            }
        }

        return engineTag;
    }

    private static Map<Item, Integer> computeMaterials(BlockEntity be) {
        Map<Item, Integer> materials = new LinkedHashMap<>();

        if (!(be instanceof IMultiStateBlockEntity multiState)) {
            return materials;
        }

        try {
            IMultiStateObjectStatistics statistics = multiState.getStatistics();
            int bitsPerBlock = Math.max(1, StateEntrySize.current().getBitsPerBlock());

            for (Map.Entry<IBlockInformation, Integer> entry : statistics.getStateCounts().entrySet()) {
                IBlockInformation info = entry.getKey();

                if (info == null || info.isAir()) {
                    continue;
                }

                int bits = entry.getValue() == null ? 0 : entry.getValue();

                if (bits <= 0) {
                    continue;
                }

                Item item = info.getBlockState().getBlock().asItem();

                if (item == Items.AIR) {
                    continue;
                }

                int fullBlocks = (bits + bitsPerBlock - 1) / bitsPerBlock;
                materials.merge(item, fullBlocks, Integer::sum);
            }
        } catch (Throwable ignored) {
        }

        return materials;
    }

    private static CompoundTag getChiseledMetadata(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null || !blockMetadata.contains(StructureToolKeys.CLONE_KEY_CHISELED, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_CHISELED);
    }

    private static boolean isChiseled(@Nullable BlockEntity be, @Nullable CompoundTag rawBeTag) {
        return be instanceof IMultiStateBlockEntity
                || (rawBeTag != null && StructureToolKeys.CHISELED_BE_ID.equals(rawBeTag.getString(NBT_ID)));
    }

    private static boolean isStorageLoaded(IMultiStateBlockEntity multiState) {
        try {
            return !multiState.getStatistics().getStateCounts().isEmpty();
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static void applyOps(IMultiStateBlockEntity multiState, int[] ops) {
        for (int op : ops) {
            switch (op) {
                case StructureToolKeys.CHISELED_OP_ROTATE_CW -> multiState.rotate(Direction.Axis.Y, 1);
                case StructureToolKeys.CHISELED_OP_ROTATE_180 -> multiState.rotate(Direction.Axis.Y, 2);
                case StructureToolKeys.CHISELED_OP_ROTATE_CCW -> multiState.rotate(Direction.Axis.Y, 3);
                case StructureToolKeys.CHISELED_OP_MIRROR_Z -> multiState.mirror(Direction.Axis.Z);
                case StructureToolKeys.CHISELED_OP_MIRROR_X -> multiState.mirror(Direction.Axis.X);
                case StructureToolKeys.CHISELED_OP_MIRROR_Y -> multiState.mirror(Direction.Axis.Y);
                case StructureToolKeys.CHISELED_OP_ROTATE_X_CW -> multiState.rotate(Direction.Axis.X, 1);
                case StructureToolKeys.CHISELED_OP_ROTATE_X_CCW -> multiState.rotate(Direction.Axis.X, 3);
                case StructureToolKeys.CHISELED_OP_ROTATE_Z_CW -> multiState.rotate(Direction.Axis.Z, 1);
                case StructureToolKeys.CHISELED_OP_ROTATE_Z_CCW -> multiState.rotate(Direction.Axis.Z, 3);
                default -> {
                }
            }
        }
    }

    private static void finishApply(ServerLevel level, BlockPos pos, int[] ops) {
        if (!(level.getBlockEntity(pos) instanceof IMultiStateBlockEntity multiState)) {
            return;
        }

        try {
            applyOps(multiState, ops);

            if (multiState instanceof BlockEntity blockEntity) {
                blockEntity.setChanged();
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void restoreChiseledBlock(ServerLevel level, BlockPos pos, @Nullable BlockState templateState) {
        if (level.getBlockEntity(pos) instanceof IMultiStateBlockEntity || templateState == null) {
            return;
        }

        level.setBlock(pos, templateState, 3);
    }

    private static void ensureRegistered() {
        if (!registered) {
            synchronized (ChiseledBitsStructureExtension.class) {
                if (!registered) {
                    MinecraftForge.EVENT_BUS.register(ChiseledBitsStructureExtension.class);
                    registered = true;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.removeIf(pending -> pending.level == level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        List<PendingApply> ready = new ArrayList<>();
        List<PendingApply> restores = new ArrayList<>();

        PENDING.removeIf(pending -> {
            if (pending.level != level) {
                return false;
            }

            boolean loaded = level.getBlockEntity(pending.pos) instanceof IMultiStateBlockEntity multiState
                    && isStorageLoaded(multiState);

            if (loaded) {
                ready.add(pending);
                return true;
            }

            if (pending.engineTag != null && now - pending.placedGameTime >= RESTORE_WAIT_TICKS) {
                restores.add(pending);
                return true;
            }

            if (pending.engineTag == null && now - pending.placedGameTime >= MAX_LOAD_WAIT_TICKS) {
                ready.add(pending);
                return true;
            }

            return false;
        });

        for (PendingApply pending : restores) {
            restoreChiseledBlock(level, pending.pos, pending.templateState);

            if (!(level.getBlockEntity(pending.pos) instanceof IMultiStateBlockEntity multiState)) {
                continue;
            }

            multiState.deserializeNBT(pending.engineTag);
            PENDING.add(new PendingApply(level, pending.pos, pending.ops, null, null, now));
        }

        for (PendingApply pending : ready) {
            finishApply(level, pending.pos, pending.ops);
        }
    }

    private record PendingApply(
            ServerLevel level,
            BlockPos pos,
            int[] ops,
            @Nullable CompoundTag engineTag,
            @Nullable BlockState templateState,
            long placedGameTime) {
    }
}
