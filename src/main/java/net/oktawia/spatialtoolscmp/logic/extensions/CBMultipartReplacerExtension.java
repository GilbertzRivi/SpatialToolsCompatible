package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import codechicken.multipart.api.ItemMultipart;
import codechicken.multipart.api.part.MultiPart;
import codechicken.multipart.block.TileMultipart;
import codechicken.multipart.util.MultipartPlaceContext;

import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtension;

public final class CBMultipartReplacerExtension implements ReplacerExtension {

    private static final String TARGET_PARTS_KEY = "spatialMultipartParts";
    private static final String NBT_PARTS = "parts";

    @Override
    public boolean canHandleSource(ServerLevel level, BlockPos pos, BlockState state) {
        return partItem(level, pos) != null;
    }

    @Override
    public Set<BlockPos> findReplacementTargets(
            ServerLevel level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx) {
        Item sourceItem = partItem(level, startPos);

        if (sourceItem == null) {
            return Set.of();
        }

        return PortableSpatialReplacer.floodFill(
                level,
                startPos,
                ctx,
                (checkedLevel, pos) -> partItem(checkedLevel, pos) == sourceItem);
    }

    @Nullable
    @Override
    public ItemStack pickTargetItem(ServerLevel level, BlockPos pos, BlockState state) {
        Item item = partItem(level, pos);

        if (item == null) {
            return null;
        }

        ItemStack picked = new ItemStack(item);
        CompoundTag parts = savedParts(level, pos);

        if (parts != null) {
            picked.getOrCreateTag().put(TARGET_PARTS_KEY, parts);
        }

        return picked;
    }

    @Nullable
    @Override
    public List<ItemStack> getPlacementCost(ServerLevel level, ItemStack target) {
        return hasStashedParts(target) ? List.of(strippedPart(target)) : null;
    }

    @Nullable
    private static CompoundTag savedParts(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TileMultipart tile)) {
            return null;
        }

        try {
            CompoundTag saved = tile.saveWithoutMetadata();

            if (!saved.contains(NBT_PARTS, Tag.TAG_LIST)) {
                return null;
            }

            CompoundTag parts = new CompoundTag();
            parts.put(NBT_PARTS, saved.getList(NBT_PARTS, Tag.TAG_COMPOUND).copy());

            return parts;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean hasStashedParts(ItemStack target) {
        CompoundTag tag = target.getTag();

        return tag != null && tag.contains(TARGET_PARTS_KEY, Tag.TAG_COMPOUND);
    }

    @Nullable
    static CompoundTag stashedParts(ItemStack target) {
        CompoundTag tag = target.getTag();

        return tag != null && tag.contains(TARGET_PARTS_KEY, Tag.TAG_COMPOUND)
                ? tag.getCompound(TARGET_PARTS_KEY)
                : null;
    }

    static ItemStack strippedPart(ItemStack target) {
        ItemStack stripped = target.copy();
        stripped.setCount(1);

        CompoundTag tag = stripped.getTag();

        if (tag != null) {
            tag.remove(TARGET_PARTS_KEY);

            if (tag.isEmpty()) {
                stripped.setTag(null);
            }
        }

        return stripped;
    }

    @Nullable
    @Override
    public Block resolveTargetBlock(ServerLevel level, ItemStack target) {
        return target.getItem() instanceof ItemMultipart ? multipartBlock() : null;
    }

    @Override
    public boolean canHandleTarget(ServerLevel level, Block targetBlock) {
        return targetBlock == multipartBlock();
    }

    @Override
    public boolean needsReplacement(ServerLevel level, BlockPos pos, ItemStack target) {
        Item current = partItem(level, pos);

        return current != null
                && target.getItem() instanceof ItemMultipart
                && current != target.getItem();
    }

    @Nullable
    @Override
    public ItemStack getInPlaceSwapItem(ServerLevel level, BlockPos pos, ItemStack target) {
        Item current = partItem(level, pos);

        return current != null && target.getItem() instanceof ItemMultipart
                ? new ItemStack(current)
                : null;
    }

    @Override
    public boolean placeTarget(
            ServerLevel level,
            BlockPos pos,
            ItemStack target,
            @Nullable ServerPlayer player) {
        if (player == null || !(target.getItem() instanceof ItemMultipart multipartItem)) {
            return false;
        }

        ItemStack placed = strippedPart(target);

        for (Direction clickedFace : PLACEMENT_FACES) {
            if (tryPlace(level, pos, multipartItem, placed, player, clickedFace)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryPlace(
            ServerLevel level,
            BlockPos pos,
            ItemMultipart multipartItem,
            ItemStack placed,
            ServerPlayer player,
            Direction clickedFace) {
        ItemStack held = player.getMainHandItem();

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, placed);

            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(pos).relative(clickedFace.getOpposite(), 0.5D),
                    clickedFace,
                    pos,
                    false);

            MultipartPlaceContext context = new MultipartPlaceContext(player, InteractionHand.MAIN_HAND, hit);

            MultiPart part = multipartItem.newPart(context);

            if (part == null || !TileMultipart.canPlacePart(context, part)) {
                return false;
            }

            return TileMultipart.addPart(level, pos, part) != null;
        } catch (Throwable ignored) {
            return false;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
    }

    @Nullable
    static Item partItem(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TileMultipart tile)) {
            return null;
        }

        try {
            for (MultiPart part : tile.getPartList()) {
                for (ItemStack drop : part.getDrops()) {
                    if (!drop.isEmpty()) {
                        return drop.getItem();
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static final Direction[] PLACEMENT_FACES = {
            Direction.UP,
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private static final ResourceLocation MULTIPART_BLOCK_ID = new ResourceLocation("cb_multipart", "multipart");

    private static Block multipartBlock = null;

    @Nullable
    static Block multipartBlock() {
        if (multipartBlock == null) {
            multipartBlock = ForgeRegistries.BLOCKS.getValue(MULTIPART_BLOCK_ID);
        }

        return multipartBlock;
    }
}
