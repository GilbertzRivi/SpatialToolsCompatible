package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.NetworkEvent;
import net.oktawia.spatialtoolscmp.client.misc.ClonerStructureLibraryClientCache;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncClonerLibraryPacket {

    private final List<Entry> entries;
    private final String selectedId;
    private final List<String> folders;

    public SyncClonerLibraryPacket(
            List<Entry> entries,
            String selectedId,
            List<String> folders
    ) {
        this.entries = List.copyOf(entries);
        this.selectedId = selectedId == null ? "" : selectedId;
        this.folders = List.copyOf(folders);
    }

    public record Entry(
            String id,
            String name,
            long created,
            long updated,
            int blockCount,
            String folder
    ) {
    }

    public static SyncClonerLibraryPacket fromStoreEntries(
            List<ClonerStructureLibraryStore.Entry> storeEntries,
            String selectedId
    ) {
        return fromStoreEntries(storeEntries, selectedId, List.of());
    }

    public static SyncClonerLibraryPacket fromStoreEntries(
            List<ClonerStructureLibraryStore.Entry> storeEntries,
            String selectedId,
            List<String> folders
    ) {
        List<Entry> entries = new ArrayList<>();

        for (ClonerStructureLibraryStore.Entry entry : storeEntries) {
            entries.add(new Entry(
                    entry.id(),
                    entry.name(),
                    entry.created(),
                    entry.updated(),
                    entry.blockCount(),
                    entry.folder() == null ? "" : entry.folder()
            ));
        }

        return new SyncClonerLibraryPacket(entries, selectedId, folders);
    }

    public static SyncClonerLibraryPacket fromPlayer(
            MinecraftServer server,
            UUID owner,
            String selectedId
    ) throws IOException {
        return fromStoreEntries(
                ClonerStructureLibraryStore.list(server, owner),
                selectedId,
                ClonerStructureLibraryStore.listFolders(server, owner)
        );
    }

    public static void encode(SyncClonerLibraryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.selectedId, 32767);
        buffer.writeVarInt(packet.entries.size());

        for (Entry entry : packet.entries) {
            buffer.writeUtf(entry.id(), 32767);
            buffer.writeUtf(entry.name(), ClonerStructureLibraryStore.MAX_NAME_LENGTH);
            buffer.writeLong(entry.created());
            buffer.writeLong(entry.updated());
            buffer.writeVarInt(Math.max(0, entry.blockCount()));
            buffer.writeUtf(entry.folder() == null ? "" : entry.folder(), ClonerStructureLibraryStore.MAX_NAME_LENGTH);
        }

        buffer.writeVarInt(packet.folders.size());

        for (String folder : packet.folders) {
            buffer.writeUtf(folder, ClonerStructureLibraryStore.MAX_NAME_LENGTH);
        }
    }

    public static SyncClonerLibraryPacket decode(FriendlyByteBuf buffer) {
        String selectedId = buffer.readUtf(32767);
        int size = buffer.readVarInt();

        List<Entry> entries = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    buffer.readUtf(32767),
                    buffer.readUtf(ClonerStructureLibraryStore.MAX_NAME_LENGTH),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readUtf(ClonerStructureLibraryStore.MAX_NAME_LENGTH)
            ));
        }

        int folderCount = buffer.readVarInt();
        List<String> folders = new ArrayList<>();

        for (int i = 0; i < folderCount; i++) {
            folders.add(buffer.readUtf(ClonerStructureLibraryStore.MAX_NAME_LENGTH));
        }

        return new SyncClonerLibraryPacket(entries, selectedId, folders);
    }

    public static void handle(SyncClonerLibraryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            List<ClonerStructureLibraryClientCache.Entry> cached = new ArrayList<>();

            for (Entry entry : packet.entries) {
                cached.add(new ClonerStructureLibraryClientCache.Entry(
                        entry.id(),
                        entry.name(),
                        entry.created(),
                        entry.updated(),
                        entry.blockCount(),
                        entry.folder() == null ? "" : entry.folder()
                ));
            }

            ClonerStructureLibraryClientCache.set(cached, packet.selectedId, packet.folders);
        });

        context.setPacketHandled(true);
    }
}