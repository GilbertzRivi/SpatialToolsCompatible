package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.*;
import java.util.function.Consumer;

import com.direwolf20.laserio.common.blockentities.basebe.BaseLaserBE;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;

public final class LaserIOStructureExtension implements StructureCloneExtension {

    private static final String[] SIDE_INVENTORY_KEYS = {
            "Inventory0", "Inventory1", "Inventory2",
            "Inventory3", "Inventory4", "Inventory5"
    };

    private static final String NBT_CONNECTION_OFFSETS = "connectionOffsets";
    private static final long NEXT_TICK_DELAY = 1L;

    private static final List<PendingConnectionInit> PENDING = new ArrayList<>();
    private static boolean registered = false;

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && blockId.toString().startsWith(StructureToolKeys.LASERIO_ID_PREFIX)) {
            return true;
        }

        return isLaserIOTag(rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        if (!isLaserIOTag(rawBeTag)) {
            return false;
        }

        addBaseBlockRequirement(level, pos, requirements);

        CompoundTag laserData = new CompoundTag();

        NbtUtil.copyIntIfPresent(rawBeTag, laserData, "laserColor");
        NbtUtil.copyByteIfPresent(rawBeTag, laserData, "showParticles");
        NbtUtil.copyIntIfPresent(rawBeTag, laserData, "wrenchAlpha");

        for (String invKey : SIDE_INVENTORY_KEYS) {
            if (rawBeTag.contains(invKey, Tag.TAG_COMPOUND)) {
                CompoundTag inv = rawBeTag.getCompound(invKey).copy();
                laserData.put(invKey, inv);
                collectInvRequirements(inv, requirements::add);
            }
        }

        ListTag rawConnections = rawBeTag.getList("connections", Tag.TAG_COMPOUND);
        if (!rawConnections.isEmpty()) {
            laserData.put(NBT_CONNECTION_OFFSETS, rawConnections.copy());
        }

        if (laserData.isEmpty()) {
            return false;
        }

        blockEntry.put(StructureToolKeys.CLONE_KEY_LASERIO, laserData);
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
        if (!isLaserIOTag(rawBeTag)) {
            return Optional.empty();
        }

        CompoundTag laserData = getLaserData(blockMetadata);
        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(state));

        if (baseItem.isEmpty() && !player.isCreative()) {
            return Optional.of(PlacementPlan.none());
        }

        List<ItemStack> costs = new ArrayList<>();

        if (!baseItem.isEmpty()) {
            costs.add(baseItem);
        }

        if (player.isCreative()) {
            for (String invKey : SIDE_INVENTORY_KEYS) {
                if (laserData.contains(invKey, Tag.TAG_COMPOUND)) {
                    collectInvRequirements(laserData.getCompound(invKey), costs::add);
                }
            }

            return Optional.of(new PlacementPlan(true, state, buildBeTag(rawBeTag, laserData), costs));
        }

        Map<Item, Integer> reserved = new LinkedHashMap<>();

        if (!baseItem.isEmpty() && !ctx.canReserveForPaste(reserved, baseItem, 1)) {
            return Optional.of(PlacementPlan.none());
        }

        CompoundTag filteredData = new CompoundTag();
        NbtUtil.copyIntIfPresent(laserData, filteredData, "laserColor");
        NbtUtil.copyByteIfPresent(laserData, filteredData, "showParticles");
        NbtUtil.copyIntIfPresent(laserData, filteredData, "wrenchAlpha");
        NbtUtil.copyTagIfPresent(laserData, filteredData, NBT_CONNECTION_OFFSETS);

        for (String invKey : SIDE_INVENTORY_KEYS) {
            if (!laserData.contains(invKey, Tag.TAG_COMPOUND)) {
                continue;
            }

            filteredData.put(invKey, filterInvForPaste(
                    laserData.getCompound(invKey), reserved, costs, ctx));
        }

        return Optional.of(new PlacementPlan(true, state, buildBeTag(rawBeTag, filteredData), costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        CompoundTag laserData = getLaserData(blockMetadata);

        if (laserData.isEmpty()) {
            return;
        }

        if (!laserData.contains(NBT_CONNECTION_OFFSETS, Tag.TAG_LIST)) {
            return;
        }

        ListTag offsets = laserData.getList(NBT_CONNECTION_OFFSETS, Tag.TAG_COMPOUND);

        if (offsets.isEmpty()) {
            return;
        }

        ensureRegistered();

        PENDING.add(new PendingConnectionInit(
                level,
                pos.immutable(),
                laserData.copy(),
                level.getGameTime() + NEXT_TICK_DELAY));
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds) {
        CompoundTag tag = saveTag(be);

        if (!handlesRequirements(state, tag)) {
            return false;
        }

        addBaseBlockRefund(level, pos, refunds);

        if (tag == null) {
            return true;
        }

        for (String invKey : SIDE_INVENTORY_KEYS) {
            if (tag.contains(invKey, Tag.TAG_COMPOUND)) {
                collectInvRequirements(tag.getCompound(invKey), refunds::add);
            }
        }

        return true;
    }

    private static void ensureRegistered() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(LaserIOStructureExtension.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.removeIf(p -> p.level == level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long now = level.getGameTime();
        Iterator<PendingConnectionInit> iterator = PENDING.iterator();

        while (iterator.hasNext()) {
            PendingConnectionInit pending = iterator.next();

            if (pending.level != level) {
                continue;
            }

            if (now < pending.runAtGameTime) {
                continue;
            }

            applyConnectionsDeferred(level, pending.nodePos, pending.laserData);
            iterator.remove();
        }
    }

    private static void applyConnectionsDeferred(
            ServerLevel level,
            BlockPos nodePos,
            CompoundTag laserData) {
        BlockEntity be = level.getBlockEntity(nodePos);

        if (!(be instanceof BaseLaserBE thisNode)) {
            return;
        }

        ListTag offsets = laserData.getList(NBT_CONNECTION_OFFSETS, Tag.TAG_COMPOUND);

        for (int i = 0; i < offsets.size(); i++) {
            CompoundTag entry = offsets.getCompound(i);

            if (!entry.contains("pos", Tag.TAG_COMPOUND)) {
                continue;
            }

            BlockPos offset = NbtUtils.readBlockPos(entry.getCompound("pos"));
            BlockPos targetPos = nodePos.offset(offset.getX(), offset.getY(), offset.getZ());

            BlockEntity targetBe = level.getBlockEntity(targetPos);

            if (!(targetBe instanceof BaseLaserBE targetNode)) {
                continue;
            }

            if (thisNode.isNodeConnected(targetPos)) {
                continue;
            }

            try {
                thisNode.addConnection(targetPos, targetNode);
            } catch (Throwable ignored) {
            }
        }
    }

    private record PendingConnectionInit(
            ServerLevel level,
            BlockPos nodePos,
            CompoundTag laserData,
            long runAtGameTime) {
    }

    private static CompoundTag filterInvForPaste(
            CompoundTag inv,
            Map<Item, Integer> reserved,
            List<ItemStack> costs,
            ClonerPasteContext ctx) {
        CompoundTag out = new CompoundTag();
        NbtUtil.copyIntIfPresent(inv, out, "Size");

        ListTag originalItems = getItems(inv);
        ListTag filteredItems = new ListTag();

        for (int i = 0; i < originalItems.size(); i++) {
            CompoundTag slot = originalItems.getCompound(i);

            if (canAffordSlot(slot, new LinkedHashMap<>(reserved), ctx)) {
                reserveSlotCosts(slot, reserved, costs, ctx);
                filteredItems.add(slot.copy());
            }
        }

        out.put("Items", filteredItems);
        return out;
    }

    private static boolean canAffordSlot(
            CompoundTag slot,
            Map<Item, Integer> probe,
            ClonerPasteContext ctx) {
        ItemStack stack = readItem(slot);

        if (stack.isEmpty()) {
            return true;
        }

        if (!ctx.canReserveForPaste(probe, stack, readCount(slot))) {
            return false;
        }

        if (!slot.contains("tag", Tag.TAG_COMPOUND)) {
            return true;
        }

        return canAffordCardInv(slot.getCompound("tag"), probe, ctx);
    }

    private static boolean canAffordCardInv(
            CompoundTag cardTag,
            Map<Item, Integer> probe,
            ClonerPasteContext ctx) {
        if (!cardTag.contains("inv", Tag.TAG_COMPOUND)) {
            return true;
        }

        ListTag items = getItems(cardTag.getCompound("inv"));

        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemSlot = items.getCompound(i);
            ItemStack stack = readItem(itemSlot);

            if (stack.isEmpty()) {
                continue;
            }

            if (!ctx.canReserveForPaste(probe, stack, readCount(itemSlot))) {
                return false;
            }
        }

        return true;
    }

    private static void reserveSlotCosts(
            CompoundTag slot,
            Map<Item, Integer> reserved,
            List<ItemStack> costs,
            ClonerPasteContext ctx) {
        ItemStack stack = readItem(slot);

        if (stack.isEmpty()) {
            return;
        }

        int count = readCount(slot);
        ctx.canReserveForPaste(reserved, stack, count);
        addCost(costs, stack, count);

        if (slot.contains("tag", Tag.TAG_COMPOUND)) {
            reserveCardInvCosts(slot.getCompound("tag"), reserved, costs, ctx);
        }
    }

    private static void reserveCardInvCosts(
            CompoundTag cardTag,
            Map<Item, Integer> reserved,
            List<ItemStack> costs,
            ClonerPasteContext ctx) {
        if (!cardTag.contains("inv", Tag.TAG_COMPOUND)) {
            return;
        }

        ListTag items = getItems(cardTag.getCompound("inv"));

        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemSlot = items.getCompound(i);
            ItemStack stack = readItem(itemSlot);

            if (stack.isEmpty()) {
                continue;
            }

            int count = readCount(itemSlot);
            ctx.canReserveForPaste(reserved, stack, count);
            addCost(costs, stack, count);
        }
    }

    private static void collectInvRequirements(CompoundTag inv, Consumer<ItemStack> sink) {
        ListTag items = getItems(inv);

        for (int i = 0; i < items.size(); i++) {
            collectSlotRequirements(items.getCompound(i), sink);
        }
    }

    private static void collectSlotRequirements(CompoundTag slot, Consumer<ItemStack> sink) {
        ItemStack stack = readItem(slot);

        if (stack.isEmpty()) {
            return;
        }

        addCostToSink(sink, stack, readCount(slot));

        if (slot.contains("tag", Tag.TAG_COMPOUND)) {
            collectCardInvRequirements(slot.getCompound("tag"), sink);
        }
    }

    private static void collectCardInvRequirements(CompoundTag cardTag, Consumer<ItemStack> sink) {
        if (!cardTag.contains("inv", Tag.TAG_COMPOUND)) {
            return;
        }

        ListTag items = getItems(cardTag.getCompound("inv"));

        for (int i = 0; i < items.size(); i++) {
            CompoundTag itemSlot = items.getCompound(i);
            ItemStack stack = readItem(itemSlot);

            if (stack.isEmpty()) {
                continue;
            }

            addCostToSink(sink, stack, readCount(itemSlot));
        }
    }

    private static CompoundTag buildBeTag(@Nullable CompoundTag rawBeTag, CompoundTag laserData) {
        CompoundTag out = new CompoundTag();

        if (rawBeTag != null) {
            NbtUtil.copyStringIfPresent(rawBeTag, out, "id");
        }

        NbtUtil.copyIntIfPresent(laserData, out, "laserColor");
        NbtUtil.copyByteIfPresent(laserData, out, "showParticles");
        NbtUtil.copyIntIfPresent(laserData, out, "wrenchAlpha");

        for (String invKey : SIDE_INVENTORY_KEYS) {
            if (laserData.contains(invKey, Tag.TAG_COMPOUND)) {
                out.put(invKey, laserData.getCompound(invKey).copy());
            }
        }

        return out;
    }

    private static CompoundTag getLaserData(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null
                || !blockMetadata.contains(StructureToolKeys.CLONE_KEY_LASERIO, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_LASERIO);
    }

    private static boolean isLaserIOTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = tag.getString("id");
        return !id.isBlank() && id.startsWith(StructureToolKeys.LASERIO_ID_PREFIX);
    }

    private static ItemStack readItem(CompoundTag slot) {
        String id = slot.getString("id");

        if (id.isBlank()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation loc = ResourceLocation.tryParse(id);

        if (loc == null) {
            return ItemStack.EMPTY;
        }

        Item item = ForgeRegistries.ITEMS.getValue(loc);

        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, 1);
    }

    private static int readCount(CompoundTag slot) {
        return Math.max(1, slot.getByte("Count"));
    }

    private static ListTag getItems(CompoundTag inv) {
        if (!inv.contains("Items", Tag.TAG_LIST)) {
            return new ListTag();
        }

        return inv.getList("Items", Tag.TAG_COMPOUND);
    }

    private static void addCost(List<ItemStack> costs, ItemStack stack, int count) {
        ItemStack cost = stack.copy();
        cost.setCount(count);
        costs.add(cost);
    }

    private static void addCostToSink(Consumer<ItemStack> sink, ItemStack stack, int count) {
        ItemStack cost = stack.copy();
        cost.setCount(count);
        sink.accept(cost);
    }

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);
        copy.setTag(null);
        return copy;
    }

    private static void addBaseBlockRequirement(
            ServerLevel level,
            BlockPos pos,
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            requirements.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            requirements.add(new ItemStack(item));
        }
    }

    private static void addBaseBlockRefund(ServerLevel level, BlockPos pos, List<ItemStack> refunds) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            refunds.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            refunds.add(new ItemStack(item));
        }
    }

    @Nullable
    private static CompoundTag saveTag(@Nullable BlockEntity be) {
        if (be == null) {
            return null;
        }

        try {
            return be.saveWithFullMetadata();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
