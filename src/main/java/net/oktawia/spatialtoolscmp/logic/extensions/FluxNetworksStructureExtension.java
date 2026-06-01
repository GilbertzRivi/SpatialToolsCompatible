package net.oktawia.spatialtoolscmp.logic.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FluxNetworksStructureExtension implements StructureCloneExtension {

    private static final String ID_NAMESPACE    = "fluxnetworks";
    private static final String CLONE_KEY       = "fluxnetworks";
    private static final String NBT_ID           = "id";
    private static final String NBT_CUSTOM_NAME  = "customName";
    private static final String NBT_PRIORITY     = "priority";
    private static final String NBT_SURGE_MODE   = "surgeMode";
    private static final String NBT_LIMIT        = "limit";
    private static final String NBT_DISABLE_LIMIT = "disableLimit";
    private static final String NBT_PLAYER_UUID  = "playerUUID";
    private static final String NBT_NETWORK_ID      = "networkID";
    private static final String ID_FLUX_CONTROLLER  = "fluxnetworks:flux_controller";

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId != null && ID_NAMESPACE.equals(blockId.getNamespace())) {
            return true;
        }
        return isFluxTag(rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry
    ) {
        if (!isFluxTag(rawBeTag)) {
            return false;
        }

        addBaseBlockRequirement(level, pos, requirements);

        CompoundTag data = new CompoundTag();
        NbtUtil.copyStringIfPresent(rawBeTag, data, NBT_ID);
        NbtUtil.copyStringIfPresent(rawBeTag, data, NBT_CUSTOM_NAME);
        NbtUtil.copyIntIfPresent(rawBeTag, data, NBT_PRIORITY);
        NbtUtil.copyByteIfPresent(rawBeTag, data, NBT_SURGE_MODE);
        NbtUtil.copyByteIfPresent(rawBeTag, data, NBT_DISABLE_LIMIT);
        if (rawBeTag.contains(NBT_LIMIT, Tag.TAG_LONG)) {
            data.putLong(NBT_LIMIT, rawBeTag.getLong(NBT_LIMIT));
        }
        NbtUtil.copyTagIfPresent(rawBeTag, data, NBT_PLAYER_UUID);
        if (!ID_FLUX_CONTROLLER.equals(rawBeTag.getString(NBT_ID))) {
            NbtUtil.copyIntIfPresent(rawBeTag, data, NBT_NETWORK_ID);
        }

        if (data.isEmpty()) {
            return false;
        }

        blockEntry.put(CLONE_KEY, data);
        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx
    ) {
        if (!isFluxTag(rawBeTag)) {
            return Optional.empty();
        }

        CompoundTag data = getCloneData(blockMetadata);
        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(state));

        if (baseItem.isEmpty() && !player.isCreative()) {
            return Optional.of(PlacementPlan.none());
        }

        List<ItemStack> costs = new ArrayList<>();
        if (!baseItem.isEmpty()) {
            costs.add(baseItem);
        }

        return Optional.of(new PlacementPlan(true, state, buildBeTag(rawBeTag, data), costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata
    ) {}

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds
    ) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null || !ID_NAMESPACE.equals(blockId.getNamespace())) {
            return false;
        }
        addBaseBlockRefund(level, pos, refunds);
        return true;
    }

    private static boolean isFluxTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        String id = tag.getString(NBT_ID);
        return !id.isBlank() && id.startsWith(ID_NAMESPACE + ":");
    }

    private static CompoundTag getCloneData(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null || !blockMetadata.contains(CLONE_KEY, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return blockMetadata.getCompound(CLONE_KEY);
    }

    private static CompoundTag buildBeTag(CompoundTag rawBeTag, CompoundTag data) {
        CompoundTag out = new CompoundTag();

        NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);
        NbtUtil.copyStringIfPresent(data, out, NBT_CUSTOM_NAME);
        NbtUtil.copyIntIfPresent(data, out, NBT_PRIORITY);
        NbtUtil.copyByteIfPresent(data, out, NBT_SURGE_MODE);
        NbtUtil.copyByteIfPresent(data, out, NBT_DISABLE_LIMIT);
        if (data.contains(NBT_LIMIT, Tag.TAG_LONG)) {
            out.putLong(NBT_LIMIT, data.getLong(NBT_LIMIT));
        }
        NbtUtil.copyTagIfPresent(data, out, NBT_PLAYER_UUID);
        NbtUtil.copyIntIfPresent(data, out, NBT_NETWORK_ID);

        return out;
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
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
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
}
