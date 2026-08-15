package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.client.misc.ClonerMissingBlocksClientCache;

public record SyncClonerMissingBlocksPacket(boolean missing) {

    public static void encode(SyncClonerMissingBlocksPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.missing);
    }

    public static SyncClonerMissingBlocksPacket decode(FriendlyByteBuf buffer) {
        return new SyncClonerMissingBlocksPacket(buffer.readBoolean());
    }

    public static void handle(
            SyncClonerMissingBlocksPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClonerMissingBlocksClientCache.set(packet.missing));
        context.setPacketHandled(true);
    }
}
