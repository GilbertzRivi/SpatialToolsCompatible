package net.oktawia.spatialtoolscmp.network.packets;

import java.util.Arrays;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureTransfer;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public class ExportClonerStructurePacket {

    private final int containerId;
    private final String id;

    public ExportClonerStructurePacket(int containerId, String id) {
        this.containerId = containerId;
        this.id = id == null ? "" : id;
    }

    public static void encode(ExportClonerStructurePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeUtf(packet.id, ClonerStructureTransfer.MAX_ID_LENGTH);
    }

    public static ExportClonerStructurePacket decode(FriendlyByteBuf buffer) {
        return new ExportClonerStructurePacket(
                buffer.readVarInt(),
                buffer.readUtf(ClonerStructureTransfer.MAX_ID_LENGTH));
    }

    public static void handle(ExportClonerStructurePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null || packet.id.isBlank()) {
                return;
            }

            if (!(player.containerMenu instanceof PortableSpatialClonerMenu menu)
                    || menu.containerId != packet.containerId) {
                return;
            }

            try {
                CompoundTag tag = ClonerStructureLibraryStore.load(
                        player.server,
                        player.getUUID(),
                        packet.id);

                if (tag == null) {
                    return;
                }

                byte[] bytes = TemplateUtil.compressNbt(tag);

                if (bytes.length == 0 || bytes.length > ClonerStructureTransfer.MAX_TRANSFER_BYTES) {
                    return;
                }

                NetworkHandler.sendToPlayer(player, ExportClonerStructureResultPacket.begin(packet.id));

                for (int offset = 0; offset < bytes.length; offset += ClonerStructureTransfer.CHUNK_BYTES) {
                    int end = Math.min(bytes.length, offset + ClonerStructureTransfer.CHUNK_BYTES);
                    NetworkHandler.sendToPlayer(
                            player,
                            ExportClonerStructureResultPacket.data(packet.id, Arrays.copyOfRange(bytes, offset, end)));
                }

                NetworkHandler.sendToPlayer(player, ExportClonerStructureResultPacket.end(packet.id));
            } catch (Exception ignored) {
            }
        });

        context.setPacketHandled(true);
    }
}
