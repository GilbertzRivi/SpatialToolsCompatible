package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.client.misc.ClonerStructureFileTransferClient;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureTransfer;

public class ExportClonerStructureResultPacket {

    private final int signal;
    private final String id;
    private final byte[] bytes;

    private ExportClonerStructureResultPacket(int signal, String id, byte[] bytes) {
        this.signal = signal;
        this.id = id == null ? "" : id;
        this.bytes = bytes == null ? new byte[0] : bytes;
    }

    public static ExportClonerStructureResultPacket begin(String id) {
        return new ExportClonerStructureResultPacket(ClonerStructureTransfer.SIGNAL_BEGIN, id, new byte[0]);
    }

    public static ExportClonerStructureResultPacket data(String id, byte[] bytes) {
        return new ExportClonerStructureResultPacket(ClonerStructureTransfer.SIGNAL_DATA, id, bytes);
    }

    public static ExportClonerStructureResultPacket end(String id) {
        return new ExportClonerStructureResultPacket(ClonerStructureTransfer.SIGNAL_END, id, new byte[0]);
    }

    public static void encode(ExportClonerStructureResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.signal);
        buffer.writeUtf(packet.id, ClonerStructureTransfer.MAX_ID_LENGTH);
        buffer.writeByteArray(packet.bytes);
    }

    public static ExportClonerStructureResultPacket decode(FriendlyByteBuf buffer) {
        return new ExportClonerStructureResultPacket(
                buffer.readVarInt(),
                buffer.readUtf(ClonerStructureTransfer.MAX_ID_LENGTH),
                buffer.readByteArray(ClonerStructureTransfer.CHUNK_BYTES));
    }

    public static void handle(ExportClonerStructureResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            switch (packet.signal) {
                case ClonerStructureTransfer.SIGNAL_BEGIN ->
                    ClonerStructureFileTransferClient.beginExportStream(packet.id);
                case ClonerStructureTransfer.SIGNAL_DATA ->
                    ClonerStructureFileTransferClient.appendExportStream(packet.id, packet.bytes);
                case ClonerStructureTransfer.SIGNAL_END -> ClonerStructureFileTransferClient.completeExport(packet.id);
                default -> ClonerStructureFileTransferClient.cancelExport();
            }
        });

        context.setPacketHandled(true);
    }
}
