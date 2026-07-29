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
import net.minecraft.world.level.block.Block;
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

import java.util.*;

public final class FramedBlocksClonerExtension implements StructureCloneExtension {

    private static final String MOD_ID = "framedblocks";

    private static final String NBT_ID = "id";
    private static final String NBT_CAMO = "camo";
    private static final String NBT_CAMO_TWO = "camo_two";
    private static final String NBT_GLOWING = "glowing";
    private static final String NBT_FACE = "face";
    private static final String NBT_OFFSETS = "offsets";
    private static final String NBT_STATE = "state";
    private static final String NBT_NAME = "Name";

    private static final String CLONE_KEY_FRAMED = "framed";
    private static final String CLONE_KEY_CAMO = "camo";
    private static final String CLONE_KEY_CAMO_TWO = "camo_two";
    private static final String CLONE_KEY_GLOWING = "glowing";

    private static final String[][] CAMO_KEYS = {
            {NBT_CAMO, CLONE_KEY_CAMO},
            {NBT_CAMO_TWO, CLONE_KEY_CAMO_TWO}
    };

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isFramedBlock(state, rawBeTag);
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
        if (!isFramedBlock(level.getBlockState(pos), rawBeTag)) {
            return false;
        }

        addBaseBlockRequirement(level, pos, requirements);

        CompoundTag framedData = new CompoundTag();

        if (rawBeTag != null) {
            for (String[] camoKey : CAMO_KEYS) {
                if (!rawBeTag.contains(camoKey[0], Tag.TAG_COMPOUND)) {
                    continue;
                }

                CompoundTag camoTag = rawBeTag.getCompound(camoKey[0]).copy();

                framedData.put(camoKey[1], camoTag);
                addCamoRequirement(camoTag, requirements);
            }
        }

        if (rawBeTag != null && rawBeTag.getBoolean(NBT_GLOWING)) {
            framedData.putBoolean(CLONE_KEY_GLOWING, true);
            requirements.add(new ItemStack(Items.GLOWSTONE_DUST));
        }

        if (framedData.isEmpty()) {
            return false;
        }

        blockEntry.put(CLONE_KEY_FRAMED, framedData);
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
        if (!isFramedBlock(state, rawBeTag)) {
            return Optional.empty();
        }

        if (rawBeTag == null) {
            return Optional.of(PlacementPlan.none());
        }

        CompoundTag framedData = getFramedMetadata(blockMetadata);

        Map<Item, Integer> reserved = new LinkedHashMap<>();
        List<ItemStack> costs = new ArrayList<>();

        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(state));

        if (!baseItem.isEmpty()) {
            if (!player.isCreative() && !ctx.canReserveForPaste(reserved, baseItem, 1)) {
                return Optional.of(PlacementPlan.none());
            }

            costs.add(baseItem);
        } else if (!player.isCreative()) {
            return Optional.of(PlacementPlan.none());
        }

        for (String[] camoKey : CAMO_KEYS) {
            if (!framedData.contains(camoKey[1], Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag camoTag = framedData.getCompound(camoKey[1]);
            ItemStack camoItem = normalizeSingle(getCamoRequirement(camoTag));

            if (!camoItem.isEmpty()) {
                if (!player.isCreative() && !ctx.canReserveForPaste(reserved, camoItem, 1)) {
                    return Optional.of(PlacementPlan.none());
                }

                costs.add(camoItem);
            }
        }

        boolean withGlowing = false;
        if (framedData.getBoolean(CLONE_KEY_GLOWING)) {
            ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);
            if (player.isCreative() || ctx.canReserveForPaste(reserved, glowstone, 1)) {
                costs.add(glowstone);
                withGlowing = true;
            }
        }

        CompoundTag filteredTag = createWhitelistedFramedTag(rawBeTag, framedData, withGlowing);

        return Optional.of(new PlacementPlan(true, state, filteredTag, costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata
    ) {
        if (be == null || getFramedMetadata(blockMetadata).isEmpty()) {
            return;
        }

        be.setChanged();

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static CompoundTag getFramedMetadata(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null) {
            return new CompoundTag();
        }

        if (!blockMetadata.contains(CLONE_KEY_FRAMED, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return blockMetadata.getCompound(CLONE_KEY_FRAMED);
    }

    private static CompoundTag createWhitelistedFramedTag(
            CompoundTag rawBeTag,
            CompoundTag framedData,
            boolean withGlowing
    ) {
        CompoundTag out = new CompoundTag();

        NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);

        for (String[] camoKey : CAMO_KEYS) {
            if (framedData.contains(camoKey[1], Tag.TAG_COMPOUND)) {
                out.put(camoKey[0], framedData.getCompound(camoKey[1]).copy());
            }
        }

        Tag faceTag = rawBeTag.get(NBT_FACE);
        if (faceTag != null) {
            out.put(NBT_FACE, faceTag.copy());
        }

        Tag offsetsTag = rawBeTag.get(NBT_OFFSETS);
        if (offsetsTag != null) {
            out.put(NBT_OFFSETS, offsetsTag.copy());
        }

        out.putBoolean(NBT_GLOWING, withGlowing);

        return out;
    }

    private static void addCamoRequirement(
            CompoundTag camoTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        ItemStack camoItem = getCamoRequirement(camoTag);

        if (!camoItem.isEmpty()) {
            requirements.add(camoItem);
        }
    }

    private static ItemStack getCamoRequirement(CompoundTag camoTag) {
        if (!camoTag.contains(NBT_STATE, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }

        CompoundTag stateTag = camoTag.getCompound(NBT_STATE);

        if (!stateTag.contains(NBT_NAME, Tag.TAG_STRING)) {
            return ItemStack.EMPTY;
        }

        ResourceLocation blockId = ResourceLocation.tryParse(stateTag.getString(NBT_NAME));

        if (blockId == null) {
            return ItemStack.EMPTY;
        }

        Block block = ForgeRegistries.BLOCKS.getValue(blockId);

        if (block == null) {
            return ItemStack.EMPTY;
        }

        Item item = block.asItem();

        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private static boolean isFramedBlock(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && MOD_ID.equals(blockId.getNamespace())) {
            return true;
        }

        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString(NBT_ID);

        if (id.isBlank()) {
            return false;
        }

        ResourceLocation beId = ResourceLocation.tryParse(id);

        return beId != null && MOD_ID.equals(beId.getNamespace());
    }

    private static void addBaseBlockRequirement(
            ServerLevel level,
            BlockPos pos,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

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

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setCount(1);
        copy.setTag(null);

        return copy;
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds
    ) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (!isFramedBlock(state, currentTag)) {
            return false;
        }

        addBaseBlockRefund(level, pos, refunds);

        if (currentTag != null) {
            for (String[] camoKey : CAMO_KEYS) {
                if (!currentTag.contains(camoKey[0], Tag.TAG_COMPOUND)) {
                    continue;
                }

                ItemStack camoItem = getCamoRequirement(currentTag.getCompound(camoKey[0]));

                if (!camoItem.isEmpty()) {
                    refunds.add(camoItem);
                }
            }
        }

        if (currentTag != null && currentTag.getBoolean(NBT_GLOWING)) {
            refunds.add(new ItemStack(Items.GLOWSTONE_DUST));
        }

        return true;
    }

    @Nullable
    private static CompoundTag saveCurrentTag(@Nullable BlockEntity be) {
        if (be == null) {
            return null;
        }

        try {
            return be.saveWithFullMetadata();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addBaseBlockRefund(
            ServerLevel level,
            BlockPos pos,
            List<ItemStack> refunds
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

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