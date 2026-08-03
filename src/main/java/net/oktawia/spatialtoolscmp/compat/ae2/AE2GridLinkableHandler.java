package net.oktawia.spatialtoolscmp.compat.ae2;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;

import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;

public final class AE2GridLinkableHandler implements IGridLinkableHandler {

    public static final AE2GridLinkableHandler INSTANCE = new AE2GridLinkableHandler();

    private static final String NBT_KEY = "ae2GridLink";

    private AE2GridLinkableHandler() {
    }

    @Override
    public boolean canLink(ItemStack stack) {
        return AE2Compat.isLinkable(stack);
    }

    @Override
    public void link(ItemStack stack, GlobalPos pos) {
        if (stack.getItem() instanceof PortableSpatialCloner) {
            PortableSpatialCloner.clearItemHandlerLink(stack);
        }

        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag linkTag = new CompoundTag();

        linkTag.putString("dim", pos.dimension().location().toString());
        linkTag.put("pos", NbtUtils.writeBlockPos(pos.pos()));

        tag.put(NBT_KEY, linkTag);
    }

    @Override
    public void unlink(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(NBT_KEY);
        }
    }

    @Nullable
    public static GlobalPos getLinkedPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_KEY))
            return null;

        CompoundTag linkTag = tag.getCompound(NBT_KEY);
        ResourceLocation dimId = new ResourceLocation(linkTag.getString("dim"));
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos pos = NbtUtils.readBlockPos(linkTag.getCompound("pos"));

        return GlobalPos.of(dim, pos);
    }

    @Nullable
    public static IGrid getLinkedGrid(ServerLevel level, ItemStack stack) {
        GlobalPos pos = getLinkedPos(stack);
        if (pos == null)
            return null;

        MinecraftServer server = level.getServer();
        ServerLevel targetLevel = server.getLevel(pos.dimension());
        if (targetLevel != null) {
            BlockEntity be = targetLevel.getBlockEntity(pos.pos());
            if (be instanceof IWirelessAccessPoint wap) {
                return wap.getGrid();
            }
        }
        return null;
    }

    @Nullable
    public static IWirelessAccessPoint getLinkedWirelessAccessPoint(ServerLevel level, ItemStack stack) {
        GlobalPos pos = getLinkedPos(stack);

        if (pos == null) {
            return null;
        }

        MinecraftServer server = level.getServer();
        ServerLevel targetLevel = server.getLevel(pos.dimension());

        if (targetLevel == null) {
            return null;
        }

        BlockEntity be = targetLevel.getBlockEntity(pos.pos());

        if (be instanceof IWirelessAccessPoint wap) {
            return wap;
        }

        return null;
    }
}
