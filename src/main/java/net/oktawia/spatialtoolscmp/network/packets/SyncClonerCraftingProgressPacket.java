package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.client.misc.ClonerCraftingProgressClientCache;

public record SyncClonerCraftingProgressPacket(int containerId, int state, long done, long total, String label) {

    public static void encode(SyncClonerCraftingProgressPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeVarInt(packet.state);
        buf.writeLong(packet.done);
        buf.writeLong(packet.total);
        buf.writeUtf(packet.label, 64);
    }

    public static SyncClonerCraftingProgressPacket decode(FriendlyByteBuf buf) {
        return new SyncClonerCraftingProgressPacket(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readUtf(64));
    }

    public static void handle(SyncClonerCraftingProgressPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> ClonerCraftingProgressClientCache.set(
                packet.containerId,
                new ClonerCraftingProgressClientCache.Progress(
                        packet.state,
                        packet.done,
                        packet.total,
                        packet.label)));

        context.setPacketHandled(true);
    }
}
