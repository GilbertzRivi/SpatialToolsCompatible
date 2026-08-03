package net.oktawia.spatialtoolscmp.items.helpers;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

public final class ClonerItemHandlerLink {

    private static final String ITEM_HANDLER_LINK_KEY = "itemHandlerLink";
    private static final String ITEM_HANDLER_LINK_DIM_KEY = "dim";
    private static final String ITEM_HANDLER_LINK_POS_KEY = "pos";
    private static final String ITEM_HANDLER_LINK_SIDE_KEY = "side";

    private ClonerItemHandlerLink() {
    }

    public record ItemHandlerLink(GlobalPos pos, @Nullable Direction side) {
    }

    public static boolean hasItemHandlerLink(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ITEM_HANDLER_LINK_KEY, Tag.TAG_COMPOUND);
    }

    public static void clearItemHandlerLink(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag != null) {
            tag.remove(ITEM_HANDLER_LINK_KEY);
        }
    }

    public static void setItemHandlerLink(
            ItemStack stack,
            ServerLevel level,
            BlockPos pos,
            @Nullable Direction side) {
        CompoundTag linkTag = new CompoundTag();

        linkTag.putString(ITEM_HANDLER_LINK_DIM_KEY, level.dimension().location().toString());
        linkTag.put(ITEM_HANDLER_LINK_POS_KEY, NbtUtils.writeBlockPos(pos));

        if (side != null) {
            linkTag.putString(ITEM_HANDLER_LINK_SIDE_KEY, side.getName());
        }

        stack.getOrCreateTag().put(ITEM_HANDLER_LINK_KEY, linkTag);
    }

    public static boolean hasItemHandlerCapability(BlockEntity blockEntity, @Nullable Direction side) {
        LazyOptional<IItemHandler> sided = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side);

        if (sided.isPresent()) {
            return true;
        }

        return side != null && blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();
    }

    @Nullable
    public static ItemHandlerLink getItemHandlerLink(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(ITEM_HANDLER_LINK_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag linkTag = tag.getCompound(ITEM_HANDLER_LINK_KEY);

        if (!linkTag.contains(ITEM_HANDLER_LINK_DIM_KEY, Tag.TAG_STRING)
                || !linkTag.contains(ITEM_HANDLER_LINK_POS_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        ResourceLocation dimId = ResourceLocation.tryParse(linkTag.getString(ITEM_HANDLER_LINK_DIM_KEY));

        if (dimId == null) {
            return null;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos pos = NbtUtils.readBlockPos(linkTag.getCompound(ITEM_HANDLER_LINK_POS_KEY));

        Direction side = null;

        if (linkTag.contains(ITEM_HANDLER_LINK_SIDE_KEY, Tag.TAG_STRING)) {
            side = Direction.byName(linkTag.getString(ITEM_HANDLER_LINK_SIDE_KEY));
        }

        return new ItemHandlerLink(GlobalPos.of(dimension, pos), side);
    }

    @Nullable
    public static IItemHandler getLinkedItemHandler(ServerLevel level, ItemStack toolStack) {
        ItemHandlerLink link = getItemHandlerLink(toolStack);

        if (link == null) {
            return null;
        }

        ServerLevel targetLevel = level.getServer().getLevel(link.pos().dimension());

        if (targetLevel == null) {
            return null;
        }

        BlockEntity blockEntity = targetLevel.getBlockEntity(link.pos().pos());

        if (blockEntity == null) {
            return null;
        }

        IItemHandler sided = blockEntity
                .getCapability(ForgeCapabilities.ITEM_HANDLER, link.side())
                .orElse(null);

        if (sided != null) {
            return sided;
        }

        if (link.side() != null) {
            return blockEntity
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                    .orElse(null);
        }

        return null;
    }

    public static long countLinkedItemHandlerStorage(
            ServerLevel level,
            ItemStack toolStack,
            ItemStack wanted) {
        if (level == null || toolStack.isEmpty() || wanted.isEmpty()) {
            return 0L;
        }

        IItemHandler handler = getLinkedItemHandler(level, toolStack);

        if (handler == null) {
            return 0L;
        }

        long total = 0L;

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);

            if (!inSlot.isEmpty() && ItemStack.isSameItemSameTags(inSlot, wanted)) {
                total += inSlot.getCount();
            }
        }

        return total;
    }
}
