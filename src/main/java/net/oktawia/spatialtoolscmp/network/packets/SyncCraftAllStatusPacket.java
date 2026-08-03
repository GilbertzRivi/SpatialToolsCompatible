package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.client.misc.CraftingBufferStatusClientCache;

public record SyncCraftAllStatusPacket(int containerId, int status) {

    public static void encode(SyncCraftAllStatusPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeVarInt(packet.status);
    }

    public static SyncCraftAllStatusPacket decode(FriendlyByteBuf buf) {
        return new SyncCraftAllStatusPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SyncCraftAllStatusPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CraftingBufferStatusClientCache.set(packet.containerId, packet.status));
        context.setPacketHandled(true);
    }
}
