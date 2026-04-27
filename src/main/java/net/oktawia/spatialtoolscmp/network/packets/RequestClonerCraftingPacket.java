package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;

import java.util.function.Supplier;

public record RequestClonerCraftingPacket(
        int containerId,
        ResourceLocation itemId,
        long amount
) {
    public static void encode(RequestClonerCraftingPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.containerId);
        buffer.writeResourceLocation(packet.itemId);
        buffer.writeLong(packet.amount);
    }

    public static RequestClonerCraftingPacket decode(FriendlyByteBuf buffer) {
        return new RequestClonerCraftingPacket(
                buffer.readInt(),
                buffer.readResourceLocation(),
                buffer.readLong()
        );
    }

    public static void handle(
            RequestClonerCraftingPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            if (!(player.containerMenu instanceof PortableSpatialClonerMenu menu)) {
                return;
            }

            if (menu.containerId != packet.containerId) {
                return;
            }

            menu.handleCraftRequest(packet.itemId, packet.amount);
        });

        context.setPacketHandled(true);
    }
}