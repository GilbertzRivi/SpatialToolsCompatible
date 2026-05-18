package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;

import java.util.function.Supplier;

public record RequestCraftAllPacket(int containerId) {

    public static void encode(RequestCraftAllPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
    }

    public static RequestCraftAllPacket decode(FriendlyByteBuf buf) {
        return new RequestCraftAllPacket(buf.readVarInt());
    }

    public static void handle(RequestCraftAllPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof PortableSpatialClonerMenu menu)) return;
            if (menu.containerId != packet.containerId) return;
            menu.handleCraftAll();
        });
        context.setPacketHandled(true);
    }
}
