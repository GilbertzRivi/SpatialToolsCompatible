package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetClonerNestedInventoryModePacket {

    private final int containerId;
    private final int modeId;

    public SetClonerNestedInventoryModePacket(int containerId, int modeId) {
        this.containerId = containerId;
        this.modeId = modeId;
    }

    public static void encode(SetClonerNestedInventoryModePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeVarInt(packet.modeId);
    }

    public static SetClonerNestedInventoryModePacket decode(FriendlyByteBuf buffer) {
        return new SetClonerNestedInventoryModePacket(
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public static void handle(
            SetClonerNestedInventoryModePacket packet,
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

            menu.setNestedInventoryMode(
                    PortableSpatialCloner.NestedInventoryResourceMode.byId(packet.modeId)
            );
        });

        context.setPacketHandled(true);
    }
}