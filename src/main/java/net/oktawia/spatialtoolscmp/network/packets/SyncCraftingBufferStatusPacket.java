package net.oktawia.spatialtoolscmp.network.packets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.compat.ae2.CraftingBufferMenu;

public record SyncCraftingBufferStatusPacket(
        int containerId,
        boolean hasError,
        List<ItemStack> stacks,
        List<Long> requestedAmounts,
        List<Long> bufferedAmounts) {

    public static void encode(SyncCraftingBufferStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeBoolean(packet.hasError);
        buf.writeVarInt(packet.stacks.size());

        for (int i = 0; i < packet.stacks.size(); i++) {
            buf.writeItem(packet.stacks.get(i));
            buf.writeLong(packet.requestedAmounts.get(i));
            buf.writeLong(packet.bufferedAmounts.get(i));
        }
    }

    public static SyncCraftingBufferStatusPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        boolean hasError = buf.readBoolean();
        int count = buf.readVarInt();

        List<ItemStack> stacks = new ArrayList<>(count);
        List<Long> requestedAmounts = new ArrayList<>(count);
        List<Long> bufferedAmounts = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            stacks.add(buf.readItem());
            requestedAmounts.add(buf.readLong());
            bufferedAmounts.add(buf.readLong());
        }

        return new SyncCraftingBufferStatusPacket(
                containerId,
                hasError,
                stacks,
                requestedAmounts,
                bufferedAmounts);
    }

    public static void handle(SyncCraftingBufferStatusPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();

        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();

            if (mc.screen instanceof AbstractContainerScreen<?> screen
                    && screen.getMenu() instanceof CraftingBufferMenu menu
                    && menu.containerId == packet.containerId) {
                menu.updateState(
                        packet.hasError,
                        packet.stacks,
                        packet.requestedAmounts,
                        packet.bufferedAmounts);
            }
        });

        ctx.setPacketHandled(true);
    }
}
