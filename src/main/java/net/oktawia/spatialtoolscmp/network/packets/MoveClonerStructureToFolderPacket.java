package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;

public class MoveClonerStructureToFolderPacket {

    private final int containerId;
    private final String structureId;
    private final String folderName;

    public MoveClonerStructureToFolderPacket(int containerId, String structureId, String folderName) {
        this.containerId = containerId;
        this.structureId = structureId == null ? "" : structureId;
        this.folderName = folderName == null ? "" : folderName;
    }

    public static void encode(MoveClonerStructureToFolderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeUtf(packet.structureId, 32767);
        buffer.writeUtf(packet.folderName, ClonerStructureLibraryStore.MAX_NAME_LENGTH);
    }

    public static MoveClonerStructureToFolderPacket decode(FriendlyByteBuf buffer) {
        return new MoveClonerStructureToFolderPacket(
                buffer.readVarInt(),
                buffer.readUtf(32767),
                buffer.readUtf(ClonerStructureLibraryStore.MAX_NAME_LENGTH));
    }

    public static void handle(MoveClonerStructureToFolderPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null || packet.structureId.isBlank()) {
                return;
            }

            if (!(player.containerMenu instanceof PortableSpatialClonerMenu menu)
                    || menu.containerId != packet.containerId) {
                return;
            }

            try {
                ClonerStructureLibraryStore.moveToFolder(
                        player.server,
                        player.getUUID(),
                        packet.structureId,
                        packet.folderName);

                NetworkHandler.sendToPlayer(
                        player,
                        SyncClonerLibraryPacket.fromPlayer(
                                player.server,
                                player.getUUID(),
                                StructureToolStackState.getStructureId(menu.getItemStack())));
            } catch (Exception ignored) {
            }
        });

        context.setPacketHandled(true);
    }
}
