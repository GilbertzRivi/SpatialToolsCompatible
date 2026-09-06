package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureTransfer;
import net.oktawia.spatialtoolscmp.network.transfer.ClonerStructureUploadStream;

public class ImportClonerStructurePacket {

    private final int signal;
    private final int containerId;
    private final String name;
    private final byte[] bytes;

    private ImportClonerStructurePacket(int signal, int containerId, String name, byte[] bytes) {
        this.signal = signal;
        this.containerId = containerId;
        this.name = ClonerStructureLibraryStore.sanitizeName(name);
        this.bytes = bytes == null ? new byte[0] : bytes;
    }

    public static ImportClonerStructurePacket begin(int containerId, String name) {
        return new ImportClonerStructurePacket(ClonerStructureTransfer.SIGNAL_BEGIN, containerId, name, new byte[0]);
    }

    public static ImportClonerStructurePacket data(int containerId, byte[] bytes) {
        return new ImportClonerStructurePacket(ClonerStructureTransfer.SIGNAL_DATA, containerId, "", bytes);
    }

    public static ImportClonerStructurePacket end(int containerId) {
        return new ImportClonerStructurePacket(ClonerStructureTransfer.SIGNAL_END, containerId, "", new byte[0]);
    }

    public int signal() {
        return signal;
    }

    public int containerId() {
        return containerId;
    }

    public String name() {
        return name;
    }

    public byte[] bytes() {
        return bytes;
    }

    public static void encode(ImportClonerStructurePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.signal);
        buffer.writeVarInt(packet.containerId);
        buffer.writeUtf(packet.name, ClonerStructureLibraryStore.MAX_NAME_LENGTH);
        buffer.writeByteArray(packet.bytes);
    }

    public static ImportClonerStructurePacket decode(FriendlyByteBuf buffer) {
        return new ImportClonerStructurePacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(ClonerStructureLibraryStore.MAX_NAME_LENGTH),
                buffer.readByteArray(ClonerStructureTransfer.CHUNK_BYTES));
    }

    public static void handle(ImportClonerStructurePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            ClonerStructureUploadStream.accept(player, packet);
        });

        context.setPacketHandled(true);
    }
}
