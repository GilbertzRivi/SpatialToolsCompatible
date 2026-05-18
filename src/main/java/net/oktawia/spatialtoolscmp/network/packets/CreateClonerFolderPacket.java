package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;

import java.util.function.Supplier;

public class CreateClonerFolderPacket {

    private final int containerId;
    private final String folderName;

    public CreateClonerFolderPacket(int containerId, String folderName) {
        this.containerId = containerId;
        this.folderName = folderName == null ? "" : folderName;
    }

    public static void encode(CreateClonerFolderPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeUtf(packet.folderName, ClonerStructureLibraryStore.MAX_NAME_LENGTH);
    }

    public static CreateClonerFolderPacket decode(FriendlyByteBuf buffer) {
        return new CreateClonerFolderPacket(
                buffer.readVarInt(),
                buffer.readUtf(ClonerStructureLibraryStore.MAX_NAME_LENGTH)
        );
    }

    public static void handle(CreateClonerFolderPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null || packet.folderName.isBlank()) {
                return;
            }

            if (!(player.containerMenu instanceof PortableSpatialClonerMenu menu)
                    || menu.containerId != packet.containerId) {
                return;
            }

            try {
                ClonerStructureLibraryStore.createFolder(player.server, player.getUUID(), packet.folderName);

                NetworkHandler.sendToPlayer(
                        player,
                        SyncClonerLibraryPacket.fromPlayer(
                                player.server,
                                player.getUUID(),
                                StructureToolStackState.getStructureId(menu.getItemStack())
                        )
                );
            } catch (Exception ignored) {
            }
        });

        context.setPacketHandled(true);
    }
}
