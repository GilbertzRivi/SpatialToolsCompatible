package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.oktawia.spatialtoolscmp.compat.ae2.CraftingBufferMenu;

import java.util.function.Supplier;

public record SyncCraftingBufferStatusPacket(int containerId, String status) {

    public static void encode(SyncCraftingBufferStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeUtf(packet.status);
    }

    public static SyncCraftingBufferStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncCraftingBufferStatusPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(SyncCraftingBufferStatusPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?> screen
                    && screen.getMenu() instanceof CraftingBufferMenu menu
                    && menu.containerId == packet.containerId) {
                menu.updateStatus(packet.status);
            }
        });
        ctx.setPacketHandled(true);
    }
}
